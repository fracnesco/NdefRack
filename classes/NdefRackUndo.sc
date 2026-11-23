// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackUndo - cmd-z and cmd-shift-z
//
// What the window does, step by step, undone and done again. A step is one
// thing done in the window: a mouse press to its release (a drag, a cable,
// a bar, a click), a key, a menu item, a value typed. Its beginning and end
// are told here (step, gestureBegin/End); what it changed is worked out
// from the rack as it was and as it is after, so nothing has to say what
// it does:
//
//   modules made and deleted         made again, from what they were
//   cables and values               what the node map held, control by control
//   voices, a bus, speakers          the source slot 0 had
//   playing or not
//   the window's marks               places, bends, boxes, comments, colours,
//                                    compact, pages, hidden
//
// Only what the step changed goes back - a control a controller or your
// code moved since is left where it is unless the step moved it too. What
// changes in code, outside a step, is never a step: the rack stores no
// patch, and undo stores only what the window did to it.
// =====================================================================

NdefRackUndo {
	classvar <undoList, <redoList;
	classvar <>maxSteps = 100;
	classvar depth = 0, before, removed, applying = false;

	*initClass {
		undoList = List.new;
		redoList = List.new;
		removed = [];
	}

	// ------------------------------------------------------------ steps

	// func as one step; nested in another, part of it
	*step { |func|
		var res;
		this.begin;
		res = func.protect { this.end };
		^res
	}

	*begin {
		if (applying) { ^this };
		depth = depth + 1;
		if (depth == 1) { before = this.prState; removed = [] };
	}

	*end {
		if (applying or: { depth == 0 }) { ^this };
		depth = depth - 1;
		if (depth == 0) { this.prClose };
	}

	// A mouse press: a step of its own until its release, whatever an
	// earlier press left open (a release that never came - a menu took it).
	*gestureBegin {
		this.flush;
		this.begin;
	}

	*gestureEnd { this.flush }

	// whatever is open closed now
	*flush {
		if (applying.not and: { depth > 0 }) { depth = 0; this.prClose };
	}

	// a module removal, as removeAll wrote it down: what undo makes again
	*noteRemoved { |snap|
		if (applying.not and: { depth > 0 }) { removed = removed.add(snap) };
	}

	*clear {
		undoList.clear;
		redoList.clear;
		this.changed(\undo);
	}

	*canUndo { this.flush; ^undoList.notEmpty }
	*canRedo { this.flush; ^redoList.notEmpty }

	// what cmd-z would undo, in words - nil for nothing
	*undoName { this.flush; ^undoList.last !? { |d| d[\name] } }
	*redoName { this.flush; ^redoList.last !? { |d| d[\name] } }

	// The last step undone. Answers it - name: what it was, keys: the modules
	// it brought back - or nil when there is nothing to undo.
	*undo {
		var d;
		this.flush;
		d = undoList.pop ?? { ^nil };
		d[\keys] = this.prApply(d, true);
		redoList.add(d);
		this.changed(\undo);
		^d
	}

	*redo {
		var d;
		this.flush;
		d = redoList.pop ?? { ^nil };
		d[\keys] = this.prApply(d, false);
		undoList.add(d);
		this.changed(\undo);
		^d
	}

	// ------------------------------------------------------------ the state

	*prClose {
		var after = this.prState;
		var d = this.prDiff(before, after, removed);
		before = nil;
		removed = [];
		d ?? { ^this };
		undoList.add(d);
		while { undoList.size > maxSteps } { undoList.removeAt(0) };
		redoList.clear;
		this.changed(\undo);
	}

	*prState {
		var mods = IdentityDictionary.new;
		NdefRack.modules.keysValuesDo { |key, m|
			NdefRack.prTry(key, { mods.put(key, this.prModState(m)) })
		};
		^(mods: mods, marks: this.prMarks)
	}

	*prModState { |m|
		var px = m.proxy;
		var values = IdentityDictionary.new;
		m.params.do { |p|
			// a trigger fires when it is set: never written, never set back
			if (p[\type] != \trigger) {
				values.put(p[\name], this.prValueOf(px.nodeMap.at(p[\name])))
			}
		};
		^(
			values: values,
			source: px.objects.at(0) !? (_.source),
			floor: NdefRack.voiceFloor(m.name),
			playing: m.isMonitoring
		)
	}

	// what a node map holds, as it is compared and set back: a cable as the
	// module it comes from - [\cable, key] - or the proxy itself when it is
	// not a module's; a number or an array of them as itself
	*prValueOf { |v|
		var px;
		if (v.isNil) { ^nil };
		px = if (v.isKindOf(BusPlug)) { v } {
			if (v.isArray and: { v.notEmpty } and: { v.first.isKindOf(BusPlug) }) { v.first }
		};
		px !? { ^[\cable, NdefRack.prModuleKey(px) ? v] };
		^v.copy
	}

	*prIsCable { |v|
		^v.isKindOf(BusPlug) or: { v.isArray and: { v.notEmpty } and: { v.first.isKindOf(BusPlug) } }
	}

	*prCableValue { |v| ^v.isArray and: { v.size == 2 } and: { v[0] == \cable } }

	// The window's own state, deep copied: a set as a dictionary of trues.
	*prMarks {
		var setDict = { |set| var d = Dictionary.new; set.do { |k| d.put(k, true) }; d };
		var copy = { |dict| var d = Dictionary.new; dict.keysValuesDo { |k, v| d.put(k, v.deepCopy) }; d };
		^(
			positions: copy.(NdefRack.positions),
			bends: copy.(NdefRack.bends),
			comments: copy.(NdefRack.comments),
			boxes: copy.(NdefRack.boxes),
			moduleColors: copy.(NdefRack.moduleColors),
			cableColors: copy.(NdefRack.cableColors),
			pageNums: copy.(NdefRack.pageNums),
			compacted: setDict.(NdefRack.compacted),
			hidden: setDict.(NdefRack.hidden)
		)
	}

	*prEq { |a, b|
		if (a.isKindOf(Dictionary) and: { b.isKindOf(Dictionary) }) {
			^a.size == b.size and: { a.keys.every { |k| this.prEq(a.at(k), b.at(k)) } }
		};
		if (a.isKindOf(SequenceableCollection) and: { a.isString.not }
			and: { b.isKindOf(SequenceableCollection) } and: { b.isString.not }) {
			^a.size == b.size and: { a.every { |x, i| this.prEq(x, b[i]) } }
		};
		^a == b
	}

	// What changed between two states, or nil for nothing.
	*prDiff { |was, now, gone|
		var created, removedKeys, values = [], sources = [], playing = [], marks = [];
		created = now[\mods].keys.reject { |k| was[\mods].includesKey(k) }.asArray.sort;
		removedKeys = was[\mods].keys.reject { |k| now[\mods].includesKey(k) }.asArray.sort;
		now[\mods].keysValuesDo { |key, n|
			was[\mods].at(key) !? { |w|
				if (w[\source] !== n[\source] or: { w[\floor] != n[\floor] }) {
					sources = sources.add([key, w[\source], n[\source], w[\floor], n[\floor]])
				};
				(w[\values].keys ++ n[\values].keys).do { |p|
					if (this.prEq(w[\values].at(p), n[\values].at(p)).not) {
						values = values.add([key, p, w[\values].at(p), n[\values].at(p)])
					}
				};
				if (w[\playing] != n[\playing]) { playing = playing.add([key, w[\playing], n[\playing]]) };
			}
		};
		was[\marks].keysValuesDo { |which, w|
			var n = now[\marks].at(which);
			(w.keys ++ n.keys).do { |k|
				if (this.prEq(w.at(k), n.at(k)).not) { marks = marks.add([which, k, w.at(k), n.at(k)]) }
			}
		};
		// a module that went another way than removeAll - nothing to make it
		// again from - stays gone
		gone = gone.select { |snap| snap[\modules].any { |d| removedKeys.includes(d[\name]) } };
		if (created.isEmpty and: { removedKeys.isEmpty } and: { values.isEmpty } and: { sources.isEmpty }
			and: { playing.isEmpty } and: { marks.isEmpty }) { ^nil };
		^(
			created: created, removedKeys: removedKeys, removed: gone,
			values: values, sources: sources, playing: playing, marks: marks,
			name: this.prName(created, removedKeys, values, sources, playing, marks)
		)
	}

	// the step in a few words, for the hint bar and the menu
	*prName { |created, removedKeys, values, sources, playing, marks|
		var list = { |keys| if (keys.size > 3) { "% modules".format(keys.size) } { keys.join(", ") } };
		var cables = values.select { |v| this.prCableValue(v[2]) or: { this.prCableValue(v[3]) } };
		var moved = marks.select { |m| m[0] == \positions }.collect { |m| m[1] }.asSet.asArray;
		^case
		{ removedKeys.notEmpty } { "delete %".format(list.(removedKeys)) }
		{ created.notEmpty } { "make %".format(list.(created)) }
		{ cables.size == 1 } {
			var c = cables[0];
			var v = if (this.prCableValue(c[3])) { c[3] } { c[2] };
			"cable % → % %".format(v[1], c[0], c[1])
		}
		{ cables.notEmpty } { "% cables".format(cables.size) }
		{ sources.notEmpty } { "voices, bus or speakers of %".format(list.(sources.collect(_[0]))) }
		{ values.size == 1 } { "% %".format(values[0][0], values[0][1]) }
		{ values.notEmpty } { "% values".format(values.size) }
		{ playing.notEmpty } { "play or stop %".format(list.(playing.collect(_[0]))) }
		{ moved.notEmpty and: { moved.size == marks.size } } { "move %".format(list.(moved)) }
		{ "the window's %".format(marks.collect(_[0]).asSet.asArray.collect { |w|
			(positions: "places", bends: "bends", comments: "comments", boxes: "boxes",
				moduleColors: "colours", cableColors: "cable colours", pageNums: "pages",
				compacted: "compact", hidden: "hidden")[w] ? w.asString
		}.join(", ")) }
	}

	// ------------------------------------------------------------ applying

	// The step taken back (back: true) or done again. Answers the modules it
	// made again.
	*prApply { |d, back|
		var names = IdentityDictionary.new;
		var made = [];
		var key = { |k| names.at(k) ? k };
		applying = true;
		{
			// modules made again: those deleted, undone - those made, redone
			(if (back) { d[\removed] } { d[\createdSnaps] ? [] }).do { |snap|
				var got = NdefRack.prBringBack(snap);
				names.putAll(got);
				made = made ++ got.values.asArray;
			};
			d[\sources].do { |s|
				NdefRack.at(key.(s[0])) !? { |m|
					this.prSetSource(m, if (back) { s[1] } { s[2] }, if (back) { s[3] } { s[4] })
				}
			};
			d[\values].do { |v|
				NdefRack.at(key.(v[0])) !? { |m| this.prSetValue(m, v[1], if (back) { v[2] } { v[3] }, key) }
			};
			// modules taken out: those made, undone - those deleted, redone -
			// written down first, to be made again the other way
			if (back) {
				d[\createdSnaps] = this.prTakeOut(d[\created])
			} {
				d[\removed] = this.prTakeOut(d[\removedKeys])
			};
			d[\playing].do { |p|
				NdefRack.at(key.(p[0])) !? { |m|
					var want = if (back) { p[1] } { p[2] };
					if (want != m.isMonitoring) {
						if (want) { NdefRack.play(m.name) } { NdefRack.stop(m.name) }
					}
				}
			};
			(d[\sources].collect(_[0]) ++ d[\values].collect(_[0])).asSet.do { |k|
				NdefRack.prFollow(NdefRack.at(key.(k)))
			};
			this.prSetMarks(d[\marks], back);
		}.protect { applying = false };
		^made
	}

	*prTakeOut { |keys|
		var here = keys.select { |k| NdefRack.at(k).notNil };
		var snap;
		if (here.isEmpty) { ^[] };
		snap = NdefRack.prSnapshot(here, true);
		NdefRack.removeAll(here);
		^[snap]
	}

	// voices, a bus, speakers: the source slot 0 had, put the way each is
	// changed - so whatever it feeds stays patched
	*prSetSource { |m, src, floor|
		NdefRack.prSetFloor(m.name, floor);
		if (src.isNil or: { src === (m.proxy.objects.at(0) !? (_.source)) }) { ^this };
		case
		{ NdefRackLib.busFor(src).notNil } { NdefRack.prPutBus(m, src) }
		{ NdefRackLib.speakersFor(src).notNil } { NdefRack.prPutSpeakers(m, src) }
		{
			// another width, or another module altogether (a patch loaded over
			// it): the source itself, the readers kept patched
			NdefRackLib.shapeOf(src) !? { |shape| NdefRack.prRelayMonitor(m, shape[\numChannels]) };
			NdefRack.prPut(m.proxy, 0, src, { NdefRack.prRepatchReaders(m.proxy) })
		};
	}

	// A control back to a cable, a value, or nothing set. Unplugging is
	// unplugging (disconnect: unmap, then set); unset only after it.
	*prSetValue { |m, param, v, key|
		var px = m.proxy;
		if (this.prCableValue(v)) {
			if (v[1].isKindOf(Symbol)) {
				key.(v[1]) !? { |from| if (NdefRack.at(from).notNil) { NdefRack.connect(from, m.name, param) } }
			} {
				px.set(param, v[1])
			};
			^this
		};
		if (this.prIsCable(px.nodeMap.at(param))) { NdefRack.disconnect(m.name, param) };
		if (v.isNil) {
			if (px.nodeMap.at(param).notNil) { px.unset(param) }
		} {
			px.set(param, v)
		};
	}

	*prSetMarks { |marks, back|
		var told = IdentitySet.new;
		marks.do { |mk|
			var which = mk[0], k = mk[1];
			var v = if (back) { mk[2] } { mk[3] };
			var dict = NdefRack.perform(which);
			if (dict.isKindOf(Dictionary).not) {
				if (v == true) { dict.add(k) } { dict.remove(k) }
			} {
				if (v.isNil) { dict.removeAt(k) } { dict.put(k, v.deepCopy) }
			};
			switch(which,
				\compacted, { NdefRack.changed(\compact, k) },
				\pageNums, { NdefRack.changed(\page, k) },
				{ told.add(which) }
			);
		};
		told.do { |which|
			switch(which,
				\positions, { NdefRack.changed(\positions) },
				\bends, { NdefRack.changed(\bends) },
				\comments, { NdefRack.changed(\comments) },
				\boxes, { NdefRack.changed(\boxes) },
				\moduleColors, { NdefRack.changed(\colors) },
				\cableColors, { NdefRack.changed(\colors) },
				\hidden, { NdefRack.scan }
			)
		};
	}
}
