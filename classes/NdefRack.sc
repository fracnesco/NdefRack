// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRack - every Ndef is a module, every cable is a mapping
//
// There is no module definition. Define an Ndef and it is in the rack;
// redefine it and the module follows; map one into another and there is
// a cable:
//
//   NdefRack.gui;
//   Ndef(\osc, { SinOsc.ar(\freq.kr(220, spec: \freq)) * 0.1 });
//   Ndef(\lfo, { SinOsc.kr(\rate.kr(0.3)).range(200, 400) });
//   Ndef(\osc).set(\freq, Ndef(\lfo));      // a cable, drawn in the rack
//
//   NdefRack.connect(\lfo, \osc, \freq);    // the same, from the rack's side
//   NdefRack.disconnect(\osc, \freq);       // unmap, and the value comes back
//   NdefRack.make(\verbos);                 // a module from the library
//   NdefRack.make(\wave, \pad, 4);          // ... playing four voices
//   NdefRack.setVoices(\pad, 8);            // eight, everything patched kept
//   NdefRack.make(\bus, bus: \reverb);      // what is on BusManager's \reverb
//   NdefRack.make(\bus, bus: 16, channels: 2);   // ... or on audio buses 16, 17
//   NdefRack.make(\vbap, speakers: \octo);   // a panner over eight speakers in a ring
//   NdefRack.setSpeakers(\vbap, '5.1');      // ... over 5.1, everything patched kept
//   NdefRack.setRecorded(\osc);             // a source of BusRecorder's next take
//   NdefRackKtl.follow(\m1);                // MKtl(\m1)'s knobs turn the selected module
//   NdefRack.postPatch;                     // the rack as Ndef(...).set(...) lines
//
// Voices: a library module written for them (NdefRackLib's poly:) can play
// several at once, one channel each, all set by the same controls. Voices
// pass down the chain: a cable with voices into a control with a value per
// voice makes the module as wide - and back to the voices given it (make,
// setVoices) when the cable goes. A cable from a module with voices into one
// with as many runs voice to voice; a mono cable into a module with voices
// reaches every voice; a cable with voices into a module written for one
// voice only brings the first.
//
// Buses: the library's bus module reads a bus - one of BusManager's by
// name, at the bus's rate and width, following the name when BusManager
// moves the bus; or an index of your own, at the width and rate you give
// it, which stays where it is. send, throw, sendpan, replace, blend and
// insert write to one, picked the same way. BusManager is looked up at run
// time; the rack does not need it, and never adds or removes a bus of its.
//
// Speakers: vbap and ambi place a sound among the speakers of a setup
// from NdefRackSpeakers' table - a channel per speaker, in the setup's
// order. Played, such a module goes speaker by speaker to the outputs when
// the server has as many; otherwise it is folded onto the stereo pair like
// anything wide.
//
// Recording: with BusRecorder installed, an audio module can be one of its
// sources - its Ndef's bus, under the module's name. The rack keeps no list:
// a module is recorded when BusRecorder has a source reading its bus, and
// that source is moved with the module when its width changes.
//
// The patch is never stored here. It is read from each Ndef's node map
// whenever something asks, so a cable made in code and a cable dragged
// in the window are the same thing, and the code is always the truth.
// The only state of the rack's own is where the modules sit.
//
// Everything is class side: one rack per session, watching the Ndefs of
// one server - Server.default, unless NdefRack.server says otherwise.
//
// The one thing the rack has to get right is unplugging. It is unmap,
// then set: unset leaves the server reading the bus, because it sends
// back what the node map holds for the key, and that is the proxy.
// =====================================================================

NdefRack {
	classvar server;					// read through *server, which fills it in lazily
	classvar <modules;					// key -> NdefRackModule
	classvar <positions;				// key -> Point in the window; outlives the module
	classvar <hidden;					// keys the rack leaves out
	classvar <bends;					// [from, to, param] -> where a routed cable bends, see bend
	classvar <comments;					// id -> (text:, rect:), boxes of text in the window, see addComment
	classvar <boxes;					// id -> (name:, rect:, color:), network boxes in the window, see addBox
	classvar <moduleColors;				// key -> a colour name of NdefRackGUI.palette, see setColor
	classvar <compacted;				// keys the window shows compact, see setCompact
	classvar <pageNums;					// key -> the page of controls the window shows, see setPage
	classvar <cableColors;				// [from, to, param] -> a colour name, see setCableColor
	classvar order;						// keys, in the order the modules arrived
	classvar broken;					// keys that could not be read, warned about once
	classvar <clipboard;				// what copy took, for paste
	classvar lastRemoved;				// what the last removal took, for undoRemove
	classvar <watching = false, skipJack, <guiObj;
	classvar busWatcher;				// a SimpleController on BusManager, once a bus module exists
	classvar speakerWatcher;			// one on NdefRackSpeakers, once a module plays to speakers
	classvar <>pollTime = 0.25;
	classvar voiceFloors;				// key -> the voices given a module (setVoices, make); none: 1
	classvar pendingFollow;				// keys whose width follows a cable made in code, soon
	classvar voiceExprs;				// key -> (param -> [text, the value it gave]), see setValues
	classvar <generators;				// name -> { |n, args| a value per voice }, see setValues
	classvar voiceSliders;				// key -> controls shown as a slider per voice, see showVoices

	*initClass {
		modules = IdentityDictionary.new;
		positions = IdentityDictionary.new;
		bends = Dictionary.new;
		comments = IdentityDictionary.new;
		boxes = IdentityDictionary.new;
		moduleColors = IdentityDictionary.new;
		compacted = IdentitySet.new;
		pageNums = IdentityDictionary.new;
		cableColors = Dictionary.new;
		voiceFloors = IdentityDictionary.new;
		pendingFollow = IdentitySet.new;
		voiceExprs = IdentityDictionary.new;
		voiceSliders = IdentityDictionary.new;
		generators = IdentityDictionary[
			// evenly from a to b, first voice to last
			\lin -> { |n, a| if (n < 2) { a[0] } { Array.fill(n, { |i| i / (n - 1) * (a[1] - a[0]) + a[0] }) } },
			// the same, spaced by ratio: frequencies, times
			\exp -> { |n, a| if (n < 2) { a[0] } { Array.fill(n, { |i| a[0] * ((a[1] / a[0]) ** (i / (n - 1))) }) } },
			// a around a centre: spread c w is c - w to c + w
			\spread -> { |n, a| if (n < 2) { a[0] } { Array.fill(n, { |i| i / (n - 1) * 2 - 1 * a[1] + a[0] }) } },
			// anywhere from a to b, a draw per voice
			\rand -> { |n, a| Array.fill(n, { rrand(a[0].asFloat, a[1].asFloat) }) },
			// harmonics of f: f, 2f, 3f ...
			\harm -> { |n, a| Array.fill(n, { |i| a[0] * (i + 1) }) },
			// the values given, over and over
			\alt -> { |n, a| Array.fill(n, { |i| a.wrapAt(i) }) },
			// semitones as octaves, for an oct: the notes, then an octave up
			\chord -> { |n, a| Array.fill(n, { |i| (a.wrapAt(i) + (12 * (i div: a.size))) / 12 }) }
		];
		hidden = IdentitySet.new;
		broken = IdentitySet.new;
		order = [];
	}

	// Server.default is read the first time it is needed, not in initClass,
	// where the class initialisation order does not promise it exists yet.
	*server { ^server ?? { server = Server.default } }

	*server_ { |aServer|
		if (aServer === this.server) { ^this };
		this.prDropAll;
		server = aServer;
		this.scan;
		^this
	}

	// The ProxySpace Ndef keeps for the server - nil before the first Ndef
	// there. Read, never created: looking does not make an Ndef space.
	*proxySpace { ^Ndef.all.at(this.server.name) }

	// ---------------------------------------------------------------- watching

	// Ndef has no notification for a new key, so new Ndefs are found by
	// looking, four times a second. Everything else - set, map, a new
	// source, play - reaches a module straight away as a dependant.
	*watch {
		if (watching) { ^this };
		watching = true;
		// an error in the poll would end the SkipJack and the watching with it
		// the controllers' MKtls too: one made since its bindings, or made again
		skipJack = SkipJack({
			try { this.scan; NdefRackKtl.prPoll } { |err| ("NdefRack: " ++ err.errorString).warn }
		}, { pollTime }, name: "NdefRack");
		this.scan;
		// Mapper, when it is installed: the controls become its targets
		try { NdefRackMapper.register } { |err| ("NdefRack: Mapper: " ++ err.errorString).warn };
		this.changed(\watching);
		^this
	}

	*unwatch {
		skipJack !? (_.stop);
		skipJack = nil;
		watching = false;
		this.changed(\watching);
		^this
	}

	// Compare the Ndefs there are with the modules there are. The poll calls
	// this, and so does every question about the whole rack.
	*scan {
		var space = this.proxySpace;
		var present = IdentityDictionary.new;
		var arrived = [];
		var changed = false;
		space !? {
			space.envir.keysValuesDo { |key, px|
				if (this.prShows(key, px)) { present.put(key, px) }
			}
		};
		// gone, or the same name now holding another proxy (Ndef.clear did that)
		modules.keys.asArray.do { |key|
			if (present.at(key) !== modules.at(key).proxy) {
				this.prDrop(key);
				changed = true;
			}
		};
		// one Ndef the rack cannot read must not stop it reading the others
		present.keysValuesDo { |key, px|
			var m = modules.at(key);
			if (m.isNil) {
				arrived = arrived.add(key)
			} {
				this.prTry(key, { m.prPoll })
			};
		};
		// Several at once come in the order their groups were made in - the
		// order they run in on the server, which replace, blend and insert
		// depend on and the export writes them in - and by name before they
		// have a group.
		arrived.sort { |a, b|
			var ga = present.at(a).group !? (_.nodeID) ? inf;
			var gb = present.at(b).group !? (_.nodeID) ? inf;
			if (ga == gb) { a <= b } { ga < gb }
		}.do { |key|
			this.prTry(key, {
				modules.put(key, NdefRackModule(key, present.at(key)));
				this.prAdoptVoices(modules.at(key));
				order = order.add(key);
				changed = true;
			});
		};
		if (changed) { this.changed(\modules) };
		^this
	}

	// says so once per Ndef, not four times a second
	*prTry { |key, func|
		try {
			func.value;
			broken.remove(key);
		} { |err|
			if (broken.includes(key).not) {
				broken.add(key);
				("NdefRack: cannot read Ndef(%) - %".format(key.asCompileString, err.errorString)).warn;
			};
		};
	}

	// ------------------------------------------------------------------ lookup

	// An Ndef defined a moment ago - in the same block, before the poll came
	// round - is found too: a key that is not there yet is worth one look.
	*at { |name|
		var key = this.prKey(name);
		^modules.at(key) ?? {
			this.scan;
			modules.at(key)
		}
	}

	*includes { |name| ^this.at(name).notNil }

	*names {
		this.prFresh;
		^order.copy
	}

	*size {
		this.prFresh;
		^modules.size
	}

	// Every cable, as Events: (from: \lfo, to: \osc, param: \freq, kind: \map)
	// for the node map, and (from: \lfo, to: \osc, kind: \read) for an Ndef
	// that a source function reads by itself - a wire that can be shown but
	// only unplugged by rewriting the function.
	*cables {
		var res = [];
		this.prFresh;
		order.do { |key|
			modules.at(key) !? { |m|
				m.inputs.do { |pair|
					this.prModuleKey(pair[1]) !? { |from|
						res = res.add((from: from, to: key, param: pair[0], kind: \map,
							spread: m.isSpread(pair[0])))
					}
				};
				m.reads.do { |px|
					this.prModuleKey(px) !? { |from|
						res = res.add((from: from, to: key, kind: \read))
					}
				};
			}
		};
		^res
	}

	// ------------------------------------------------------------- patching

	// Plug from's output into to's input: Ndef(to).set(param, Ndef(from)).
	// Any rate into any rate. A wider source fills an array control from its
	// first value, and lands only on the control itself otherwise. A mono
	// source into a control with a value per voice goes in once per voice -
	// Ndef(\lfo) ! 4 - so every voice hears it. A wider cable into a control
	// with a value per voice makes the module that wide: the voices pass
	// down the chain (prFollow).
	*connect { |from, to, param|
		var src = this.at(from), dst = this.at(to);
		var cable, old;
		if (src.isNil) { ^this.prNoModule(from) };
		if (dst.isNil) { ^this.prNoModule(to) };
		param = param.asSymbol;
		if (dst.param(param).isNil) {
			("NdefRack: % has no input called %"
				.format(dst.name, param.asCompileString)).warn;
			^nil
		};
		cable = dst.cableFor(param, src.proxy);
		// a narrower cable over a wider one: the server would keep the old
		// one on the channels the new one does not reach (measured
		// 2026-11-20: [0.5, 0.2, 0.3, 0.4] for a mono cable over four). So
		// the old one is unmapped first, in the same moment
		old = dst.proxy.nodeMap.at(param);
		if (old.isKindOf(BusPlug) and: { old.numChannels > this.prWidth(cable) }) {
			dst.proxy.unmap(param)
		};
		dst.proxy.set(param, cable);
		this.prFollow(dst);
		^dst
	}

	// how many channels a cable brings: a proxy's, or one per item
	*prWidth { |cable|
		^if (cable.isArray) { cable.size } { cable.tryPerform(\numChannels) ? 1 }
	}

	// Unplug: unmap, then set the value the control had before the cable came -
	// or the default, for a cable that was made in code from the start. The
	// default goes back with unset, so the node map is left as if nothing
	// had been patched; unset is safe only now, after the unmap - before it,
	// it would send the cable to the server all over again. A cable spread
	// over voices unmaps with nothing sent at all (JITLib leaves arrays to
	// set); the set or unset after it is what unplugs it on the server.
	*disconnect { |to, param|
		var dst = this.at(to);
		var p, value;
		if (dst.isNil) { ^this.prNoModule(to) };
		param = param.asSymbol;
		p = dst.param(param);
		value = dst.value(param) !? { |v| dst.fit(param, v) };
		dst.proxy.unmap(param);
		if (value.isNil or: { p.notNil and: { value == p[\default] } }) {
			dst.proxy.unset(param)
		} {
			dst.proxy.set(param, value)
		};
		// the cable may have been what made it as wide as it is
		this.prFollow(dst);
		^dst
	}

	// every cable into this module and out of it
	*unplug { |name|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		m.inputs.do { |pair| this.disconnect(m.name, pair[0]) };
		modules.do { |other|
			other.inputs.do { |pair|
				if (pair[1] === m.proxy) { this.disconnect(other.name, pair[0]) }
			}
		};
		^m
	}

	*set { |name, param, value|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		m.set(param, value);
		^m
	}

	// The first time, on the stereo pair: a mono module on both sides, a
	// stereo one left and right, anything wider - voices - spread across the
	// pair as Splay would. After that it plays wherever JITLib's monitor last
	// sent it, as long as that still covers every channel.
	*play { |name|
		var m = this.at(name);
		var px, layout;
		if (m.isNil) { ^this.prNoModule(name) };
		if (m.isAudio.not) {
			("NdefRack: % is control rate - there is nothing to hear".format(m.name)).warn;
			^nil
		};
		px = m.proxy;
		if (this.prLayoutFits(px)) {
			px.play
		} {
			layout = this.prLayoutFor(m, px.numChannels);
			px.playN(layout[0], layout[1])
		};
		^m
	}

	*stop { |name|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		m.proxy.stop;
		^m
	}

	*togglePlay { |name|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		^if (m.isMonitoring) { this.stop(name) } { this.play(name) }
	}

	// ----------------------------------------------------------------- modules

	// A library module as an Ndef, under its own name or the next free one:
	// NdefRack.make(\verbos) -> Ndef(\verbos), then \verbos_2, \verbos_3 ...
	// voices, for a module written for them: how many it plays. bus, for a
	// bus module: the name of the BusManager bus it reads or writes to - or a
	// bus index, at channels and rate (1, \audio), or a Bus. Given no name of
	// its own, a module reading a bus is called after it: NdefRack.make(\bus,
	// bus: \reverb) -> Ndef(\reverb), or \reverb_2; one writing to it is
	// called after its kind, \send. speakers, for a module panning among
	// speakers: the name of a setup in NdefRackSpeakers' table.
	*make { |kind, name, voices = 1, bus, channels, rate, speakers|
		var func;
		var onBus = this.prIsBusName(bus) and: { NdefRackLib.canBus(kind) };
		var named = onBus and: { NdefRackLib.writesBus(kind).not };
		if (NdefRackLib.canBus(kind)) {
			func = NdefRackLib.busReader(kind, bus, channels, rate, voices);
			if (onBus) { this.prCheckBusServer(kind) };
		} {
			func = if (NdefRackLib.canSpeakers(kind)) {
				NdefRackLib.speakerFunc(kind, speakers, voices)
			} {
				NdefRackLib.at(kind, voices)
			};
		};
		if (func.isNil) { ^nil };
		name = (name ?? { this.prFreeName(if (named) { bus } { kind }) }).asSymbol;
		this.prSetFloor(name, if (NdefRackLib.canVoice(kind)) { voices } { 1 });
		this.ndef(name, func);
		// made over a module that was patched: its cables may bring more voices
		this.prFollow(this.at(name));
		^Ndef(name -> this.server.name)
	}

	// The Ndef called name on the rack's server, with a new source if given.
	// The source decides the width: an Ndef that was wider or narrower takes
	// the new one's, and whatever reads it is kept patched (see prPut).
	*ndef { |name, source|
		var px = Ndef(name.asSymbol -> this.server.name);
		source !? { this.prPut(px, nil, source) };
		^px
	}

	// ------------------------------------------------------------------ voices

	// how many voices a module plays: 1 for a mono module and for your own
	*voices { |name| ^this.at(name) !? (_.voices) }

	// whether it is a library module written for voices
	*canVoice { |name| ^(this.at(name) !? (_.canVoice)) == true }

	// A control with a value per voice shown as a slider per voice, in place
	// of its one bar - only while the module plays more than one voice; the
	// choice stays through changes of voices. The window's per voice menu.
	*showVoices { |name, param, flag = true|
		var key = this.prKey(name), m = this.at(key);
		var set = voiceSliders.at(key) ?? { IdentitySet.new };
		param = param.asSymbol;
		if (flag) { set.add(param) } { set.remove(param) };
		if (set.isEmpty) { voiceSliders.removeAt(key) } { voiceSliders.put(key, set) };
		m !? {
			m.prRead;
			this.changed(\module, key);
		};
		^this
	}

	*showsVoices { |name, param|
		^(voiceSliders.at(this.prKey(name)) !? { |set| set.includes(param.asSymbol) }) == true
	}

	// the voices given a module - by setVoices, make, a patch file: the fewest
	// it plays, whatever its cables bring; 1 when none were given
	*voiceFloor { |name| ^voiceFloors.at(this.prKey(name)) ? 1 }

	// Play at least n voices - more while the widest cable into it brings more
	// (prFollow). The controls' values go to every voice, a mono cable into a
	// control with a value per voice reaches every voice, and everything
	// patched from the module stays patched, without a gap - the width change
	// is made the one way JITLib does not lose readers in (prPut). A playing
	// module goes on playing, its voices spread across the stereo pair again.
	// The module's own state starts over - phases, envelopes - and so does
	// that of what it feeds directly; it is a new source, for them a new bus.
	// What it feeds takes its new width in turn.
	*setVoices { |name, n|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		if (m.canVoice.not) {
			("NdefRack: % plays one voice - only library modules written for voices play more"
				.format(m.name)).warn;
			^nil
		};
		this.prSetFloor(m.name, n);
		this.prFollow(m);
		^m
	}

	*prSetFloor { |key, n|
		n = (n ? 1).asInteger.clip(1, NdefRackLib.maxVoices);
		if (n > 1) { voiceFloors.put(key, n) } { voiceFloors.removeAt(key) };
	}

	// The voices a module plays: those given it, or more when a cable into one
	// of its controls with a value per voice brings more - so voices pass down
	// the chain. When the library marks an input follow: (splay's in), only
	// that one counts. A mono cable spread over the voices brings one; a
	// module that names its outputs (outs: - turing's value and gate) brings
	// its voices, not its channels. Run after every cable made or taken out, and after
	// every width change of what feeds the module. A loop takes the widest of
	// its members, and stays that wide while it is closed.
	*prFollow { |m|
		var n;
		if (m.isNil or: { modules.at(m.name) !== m } or: { m.canVoice.not }) { ^this };
		n = this.prTargetVoices(m);
		if (n != m.voices) { this.prApplyVoices(m, n) };
	}

	// A module given its width in code - Ndef(\pad, NdefRackLib(\wave, 8)) -
	// keeps it: voices that no cable explains were given, so they are its
	// floor. Run when a module arrives and when its source changes.
	*prAdoptVoices { |m|
		if (m.canVoice and: { m.voices > this.voiceFloor(m.name) } and: { m.voices > this.prWidest(m) }) {
			this.prSetFloor(m.name, m.voices)
		}
	}

	*prTargetVoices { |m|
		^this.voiceFloor(m.name).max(this.prWidest(m)).clip(1, NdefRackLib.maxVoices)
	}

	// how many voices the cables into m bring, at most
	*prWidest { |m|
		var follow = NdefRackLib.follow(m.kind);
		var keys = if (follow.notNil) { [follow] } { NdefRackLib.voiceKeys(m.kind) };
		^keys.inject(1) { |w, key| w.max(this.prCableVoices(m.proxy.nodeMap.at(key))) }
	}

	// what a node map value brings, in voices
	*prCableVoices { |v|
		var src;
		if (v.tryPerform(\nodeMapMapsToControl) != true) { ^1 };
		src = this.prModuleKey(v) !? { |k| modules.at(k) };
		// a module naming its outputs (outs:) has them one after the other, each
		// as many channels as it has voices: those voices are what it brings
		if (src.notNil and: { src.kind.notNil } and: { NdefRackLib.outsFor(src.kind).notNil }) { ^src.voices };
		^v.numChannels ? 1
	}

	*prApplyVoices { |m, n|
		// the module's sibling for n voices - on the same bus, the same speakers
		var func = NdefRackLib.withVoices(m.proxy.objects.at(0) !? (_.source), n);
		var shape = NdefRackLib.shapeOf(func);
		var exprs = this.prLiveExprs(m);
		this.prWidenSettings(m, n);
		// a value per voice typed as an expression is worked out for n again
		exprs.keysValuesDo { |param, text|
			var perVoice = NdefRackLib.voiceKeys(m.kind).includes(param);
			this.prEvalValues(text, if (perVoice) { n } { 1 }) !? { |v|
				if (perVoice and: { n > 1 }) { v = v.asArray.extend(n, v.asArray.last) };
				m.proxy.set(param, v);
				voiceExprs.at(m.name).put(param, [text, v]);
			}
		};
		shape !? { this.prRelayMonitor(m, shape[\numChannels]) };
		this.prPut(m.proxy, 0, func, { this.prRepatchReaders(m.proxy) });
	}

	// A cable made in code - Ndef(\fold).set(\in, Ndef(\pulsar)) - is heard
	// through the module's notification, in whatever thread made it: the width
	// follows a moment later, once per module, out of that code's way.
	*prFollowSoon { |m|
		if (m.canVoice.not or: { pendingFollow.includes(m.name) }) { ^this };
		if (this.prTargetVoices(m) == m.voices) { ^this };
		pendingFollow.add(m.name);
		SystemClock.sched(0, {
			pendingFollow.remove(m.name);
			try { this.prFollow(modules.at(m.name)) } { |err| ("NdefRack: " ++ err.errorString).warn };
			nil
		});
	}

	// ------------------------------------------------------- a value per voice

	// Set a control from text: a number, an array, any expression giving one;
	// a Function of the voice and the number of voices, { |i, n| 100 * (i + 1) },
	// once per voice; or a generator by name with its numbers -
	//   lin 200 800      evenly from 200 on the first voice to 800 on the last
	//   exp 100 3200     the same, by ratio
	//   spread 0 0.1     0 - 0.1 to 0 + 0.1
	//   rand 200 800     a draw per voice
	//   harm 110         110, 220, 330 ...
	//   alt 0 1          0, 1, 0, 1 ...
	//   chord 0 4 7      for an oct: the notes in octaves, then an octave up
	// (NdefRack.generators; add your own: name -> { |n, args| ... }). A
	// function or a generator is remembered: when the module's voices change,
	// it is worked out again for the new number - as long as the control
	// still holds what it gave, and is not patched. Answers the value, or nil
	// when the text gives none.
	*setValues { |name, param, text|
		var m = this.at(name);
		var v, n, live;
		if (m.isNil) { ^this.prNoModule(name) };
		param = param.asSymbol;
		n = if (m.isVoiceParam(param)) { m.voices } { 1 };
		v = this.prEvalValues(text, n);
		if (v.isNil) {
			("NdefRack: % gives no number".format(text.asCompileString)).warn;
			^nil
		};
		v = m.fit(param, v);
		m.proxy.set(param, v);
		live = voiceExprs.at(m.name) ?? { IdentityDictionary.new };
		if (this.prIsExpr(text)) { live.put(param, [text, v]) } { live.removeAt(param) };
		if (live.isEmpty) { voiceExprs.removeAt(m.name) } { voiceExprs.put(m.name, live) };
		^v
	}

	// the expression a control's value came from, while it still holds what
	// the expression gave; nil otherwise
	*valuesText { |name, param|
		var m = this.at(name);
		^m !? { this.prLiveExprs(m).at(param.asSymbol) }
	}

	// a generator or a function: worth remembering, since its value depends
	// on the number of voices
	*prIsExpr { |text|
		var word = text.asString.stripWhiteSpace.split($ ).first;
		^generators.includesKey(word.asSymbol) or: {
			(try { text.asString.interpret }).isKindOf(Function)
		}
	}

	// text -> a number, or a value for each of n voices; nil when it gives none
	*prEvalValues { |text, n|
		var words = text.asString.stripWhiteSpace.split($ ).reject(_.isEmpty);
		var gen = words.first !? { generators.at(words.first.asSymbol) };
		var args, v;
		if (gen.notNil) {
			args = words.drop(1).collect { |w| try { w.interpret } };
			if (args.isEmpty or: { args.every(_.isNumber).not }) { ^nil };
			v = try { gen.value(n, args) };
		} {
			v = try { text.asString.interpret };
			if (v.isKindOf(Function)) { v = try { Array.fill(n, { |i| v.value(i, n) }) } };
		};
		^case
		{ v.isNumber } { v }
		{ v.isKindOf(SequenceableCollection) and: { v.notEmpty } and: { v.every(_.isNumber) } } {
			if (v.size == 1) { v[0] } { v.asArray }
		}
	}

	// [param, text, ...] of the expressions still live, for a copy or a patch
	// file; nil for none
	*prExprPairs { |m|
		var live = this.prLiveExprs(m);
		^if (live.isEmpty) { nil } { live.keys.asArray.sort.collect { |k| [k, live.at(k)] }.flatten(1) }
	}

	// a module made again from a copy or a patch: its expressions, holding
	// what its node map holds now
	*prTakeExprs { |key, pairs|
		var m = modules.at(key);
		if (m.isNil or: { pairs.isNil }) { ^this };
		pairs.pairsDo { |param, text|
			param = param.asSymbol;
			m.proxy.nodeMap.at(param) !? { |v|
				(voiceExprs.at(key) ?? { voiceExprs.put(key, IdentityDictionary.new); voiceExprs.at(key) })
					.put(param, [text.asString, v])
			}
		}
	}

	// param -> text, for the controls still holding what their expression
	// gave and not patched; the others are forgotten
	*prLiveExprs { |m|
		var live = voiceExprs.at(m.name);
		var res = IdentityDictionary.new;
		live ?? { ^res };
		live.keys.asArray.do { |param|
			var pair = live.at(param);
			if (m.isPatched(param).not and: { this.prSame(m.proxy.nodeMap.at(param), pair[1]) }) {
				res.put(param, pair[0])
			} {
				live.removeAt(param)
			}
		};
		if (live.isEmpty) { voiceExprs.removeAt(m.name) };
		^res
	}

	// ------------------------------------------------------------------- buses

	// what a bus module reads or writes to: the name of a BusManager bus, a
	// bus index of your own, or nil
	*bus { |name| ^this.at(name) !? (_.bus) }

	// whether it is a bus module
	*canBus { |name| ^(this.at(name) !? (_.canBus)) == true }

	// Read another bus, or write to another: one of BusManager's, by name - or
	// a bus index, at channels and rate, the ones it has now when not given,
	// or a Bus; nil for none. A bus of another width or rate is a new source,
	// made the way that keeps whatever it feeds patched (prPut), and the
	// cables into it are kept; a playing module goes on playing. A module
	// writing to a bus fades out of the old one.
	*setBus { |name, bus, channels, rate|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		if (m.canBus.not) {
			("NdefRack: % is not a bus module - NdefRack.make(\\bus, bus: \\name) makes one reading a bus,"
				" NdefRack.make(\\send, bus: \\name) one writing to it".format(m.name)).warn;
			^nil
		};
		if (bus.isNumber) {
			channels = channels ?? { m.busInfo !? { |b| b[\channels] } };
			rate = rate ?? { m.busInfo !? { |b| b[\rate] } };
		};
		if (this.prIsBusName(bus)) { this.prCheckBusServer(m.kind) };
		this.prPutBus(m, NdefRackLib.busReader(m.kind, bus, channels, rate, m.voices));
		^m
	}

	// ---------------------------------------------------------------- speakers

	// the name of the speaker setup a module pans among, or nil
	*speakers { |name| ^this.at(name) !? (_.speakers) }

	// whether it is a module panning among speakers: vbap, ambi
	*canSpeakers { |name| ^(this.at(name) !? (_.canSpeakers)) == true }

	// Pan among other speakers: a name from NdefRackSpeakers' table, a setup
	// itself, or nil for the default. Another number of speakers is a new
	// source at a new width, made the way that keeps whatever it feeds
	// patched (prPut); the cables into it and its values are kept, and a
	// playing module goes on playing, on the new speakers' outputs.
	*setSpeakers { |name, setup|
		var m = this.at(name);
		if (m.isNil) { ^this.prNoModule(name) };
		if (m.canSpeakers.not) {
			("NdefRack: % does not pan among speakers - NdefRack.make(\\vbap, speakers: \\quad) makes"
				" a module that does".format(m.name)).warn;
			^nil
		};
		this.prPutSpeakers(m, NdefRackLib.speakerFunc(m.kind, setup, m.voices));
		^m
	}

	// --------------------------------------------------------------- recording

	// BusRecorder, when it is installed - looked up by name, never needed
	*recorder { ^\BusRecorder.asClass }

	// whether a module can be recorded: an audio module, BusRecorder installed
	*canRecord { |name|
		^this.recorder.notNil and: { (this.at(name) !? (_.isAudio)) == true }
	}

	// BusRecorder's source reading this module's bus, or nil
	*recordSource { |name|
		var px = this.at(name) !? (_.proxy);
		^this.prRecordSources(px).first
	}

	*isRecorded { |name| ^this.recordSource(name).notNil }

	// Make the module a source of BusRecorder's takes, called after it - or
	// stop. A recorded module is armed again when asked to be recorded.
	*setRecorded { |name, flag = true|
		var m = this.at(name);
		var rec = this.recorder;
		var src, key;
		if (m.isNil) { ^this.prNoModule(name) };
		if (rec.isNil) {
			"NdefRack: recording needs BusRecorder, which is not installed".warn;
			^nil
		};
		src = this.recordSource(m.name);
		if (flag.not) {
			src !? { rec.remove(src.name) };
			this.changed(\state, m.name);
			^m
		};
		if (m.isAudio.not) {
			("NdefRack: % is control rate - there is nothing to record".format(m.name)).warn;
			^nil
		};
		if (rec.server !== this.server) {
			("NdefRack: BusRecorder records on %, the rack is on % - it would record"
				" those bus numbers on %".format(rec.server.name, this.server.name, rec.server.name)).warn;
		};
		if (src.notNil) {
			if (src.armed.not) { rec.arm(src.name) };
		} {
			// a source of that name that reads something else is the user's:
			// the module's gets a free name beside it
			key = m.name;
			if (rec.at(key).notNil) {
				key = (2 .. 99).collect { |i| (m.name ++ "_" ++ i).asSymbol }
					.detect { |k| rec.at(k).isNil };
			};
			rec.addBus(m.proxy.bus, key);
		};
		this.changed(\state, m.name);
		^m
	}

	// Take a module out: unplug it, clear the Ndef and forget the name - as
	// if it had never been defined. Code that defines it again brings it back,
	// and so does undoRemove, until the next removal.
	*remove { |name|
		var removed;
		if (this.at(name).isNil) { ^this.prNoModule(name) };
		removed = this.removeAll([name]);
		^removed.first
	}

	// Several at once - the window's delete. Before anything goes, all of it
	// is written down: the modules, where they sat, and every cable touching
	// them, so undoRemove can put the lot back.
	*removeAll { |names|
		var keys = this.prKeys(names);
		var removed;
		if (keys.isEmpty) { ^[] };
		lastRemoved = this.prSnapshot(keys, true);
		removed = keys.collect { |key| this.prRemoveOne(key) };
		this.changed(\modules);
		^removed
	}

	// The last removal back: the same names where they are free, the same
	// sources, values, places, colours and pages, every cable - those to modules that are
	// still in the rack too - and playing if they were. Answers the keys.
	*undoRemove {
		var snap = lastRemoved;
		var names;
		if (snap.isNil) { ^[] };
		lastRemoved = nil;
		names = this.prRealize(snap, Point(0, 0), true);
		snap[\modules].do { |d|
			var px = this.proxySpace !? { |space| space.envir.at(names.at(d[\name])) };
			if (px.notNil and: { d[\playing] == true }) {
				if (d[\outs].notNil) { px.playN(d[\outs], d[\amps], vol: d[\vol]) } { px.play(vol: d[\vol]) }
			};
			// recorded before: a source again, armed as it was
			d[\recorded] !? { |armed|
				var key = names.at(d[\name]);
				if (this.recorder.notNil and: { this.setRecorded(key, true).notNil } and: { armed.not }) {
					this.recordSource(key) !? { |src| this.recorder.disarm(src.name) }
				}
			};
		};
		^snap[\modules].collect { |d| names.at(d[\name]) }
	}

	*canUndoRemove { ^lastRemoved.notNil }

	// --------------------------------------------------------- copy and paste

	// What it takes to make the modules again goes to the clipboard: each
	// one's sources, the values its controls show, where it sat - and the cables
	// between them. A cable from or to anything outside the copy stays behind.
	*copy { |names|
		var keys = this.prKeys(names);
		if (keys.isEmpty) { ^nil };
		clipboard = this.prSnapshot(keys, false);
		^clipboard
	}

	*cut { |names|
		this.copy(names) ?? { ^[] };
		^this.removeAll(names)
	}

	// The clipboard's modules again, as new Ndefs under free names - osc
	// becomes osc_2 - with the cables between them made again. at is where the
	// group's top left lands; 40 pixels down and right of the originals when
	// nil. Nothing pasted plays until you play it. Answers the new keys.
	*paste { |at|
		var snap = clipboard;
		var names;
		if (snap.isNil) { ^[] };
		names = this.prRealize(snap,
			if (at.notNil) { at.asPoint - this.prTopLeft(snap) } { Point(40, 40) }, false);
		^snap[\modules].collect { |d| names.at(d[\name]) }
	}

	// copy and paste in one, leaving the clipboard as it was
	*duplicate { |names, offset|
		var keys = this.prKeys(names);
		var snap, made;
		if (keys.isEmpty) { ^[] };
		snap = this.prSnapshot(keys, false);
		made = this.prRealize(snap, (offset ? Point(40, 40)).asPoint, false);
		^snap[\modules].collect { |d| made.at(d[\name]) }
	}

	// Leave an Ndef out of the rack - a master bus, a utility nobody patches.
	*hide { |name|
		hidden.add(this.prKey(name));
		this.scan;
		^this
	}

	*unhide { |name|
		hidden.remove(this.prKey(name));
		this.scan;
		^this
	}

	// where the window draws a module; kept after the module goes, so an
	// Ndef that is cleared and defined again comes back to the same place
	*positionOf { |name| ^positions.at(this.prKey(name)) }

	*moveTo { |name, point|
		name = this.prKey(name);
		positions.put(name, point.asPoint);
		this.changed(\position, name);
		^this
	}

	// Where the window bends a cable drawn as a flow chart: [x] - the x of
	// its one vertical run - or [x1, y, x2] - its two vertical runs and the
	// lane between them (NdefRackRouter). nil: the window finds a way
	// itself. Like positions it is the window's business, kept here so a
	// patch file keeps it; the cable itself is in the node map.
	*bendOf { |from, to, param|
		^bends.at([this.prKey(from), this.prKey(to), param.asSymbol])
	}

	*bend { |from, to, param, bend|
		var k = [this.prKey(from), this.prKey(to), param.asSymbol];
		if (bend.isNil) { bends.removeAt(k) } { bends.put(k, bend.asArray.collect(_.asFloat)) };
		this.changed(\bends, k);
		^this
	}

	// every cable back to the way the window finds itself
	*clearBends {
		bends.clear;
		this.changed(\bends);
		^this
	}

	// ------------------------------------------------------------ comments

	// A box of text in the window, at point (the rack's coordinates), size
	// width @ height. It belongs to no Ndef: like the positions it is the
	// window's, kept here so a patch file keeps it. Answers its id.
	*addComment { |text = "", point, size|
		var id = this.prNextId("comment", comments);
		point = (point ? Point(20, 20)).asPoint;
		size = (size ? Point(220, 80)).asPoint;
		comments.put(id, (text: text.asString, rect: Rect(point.x, point.y, size.x, size.y)));
		this.changed(\comments, id);
		^id
	}

	// the comments in the order they were made
	*commentIds { ^this.prIdOrder(comments) }

	*commentText { |id| ^comments.at(id.asSymbol) !? { |c| c[\text] } }

	*commentRect { |id| ^comments.at(id.asSymbol) !? { |c| c[\rect] } }

	*setComment { |id, text|
		comments.at(id.asSymbol) !? { |c|
			c[\text] = text.asString;
			this.changed(\comments, id.asSymbol);
		};
		^this
	}

	// moved or resized: rect in the rack's coordinates
	*placeComment { |id, rect|
		comments.at(id.asSymbol) !? { |c|
			c[\rect] = rect;
			this.changed(\comments, id.asSymbol);
		};
		^this
	}

	*removeComment { |id|
		comments.removeAt(id.asSymbol) !? { this.changed(\comments, id.asSymbol) };
		^this
	}

	*clearComments {
		comments.clear;
		this.changed(\comments);
		^this
	}

	// ------------------------------------------------------------- colours

	// A colour a module is marked with - a name of NdefRackGUI.palette, nil
	// for none. The window tints its title and edge; what it carries keeps
	// its own colour. The window's, kept here so a patch file keeps it.
	*colorOf { |name| ^moduleColors.at(this.prKey(name)) }

	*setColor { |name, color|
		name = this.prKey(name);
		if (color.isNil) { moduleColors.removeAt(name) } { moduleColors.put(name, color.asSymbol) };
		this.changed(\colors, name);
		^this
	}

	// Compact: the window shows only the module's controls that are away
	// from their defaults - set, or patched - and its main row. The
	// window's, kept here so a patch file keeps it. Notifies \compact.
	*isCompact { |name| ^compacted.includes(this.prKey(name)) }

	*setCompact { |name, flag = true|
		name = this.prKey(name);
		if (flag) { compacted.add(name) } { compacted.remove(name) };
		this.changed(\compact, name);
		^this
	}

	// A module with more controls than fit (NdefRackModuleView.pageRows) has
	// them on pages, tabs under its main row: the page the window shows,
	// counted from 0. The window's, kept so a patch file keeps it.
	*pageOf { |name| ^pageNums.at(this.prKey(name)) ? 0 }

	*setPage { |name, page = 0|
		name = this.prKey(name);
		if (page.asInteger <= 0) { pageNums.removeAt(name) } { pageNums.put(name, page.asInteger) };
		this.changed(\page, name);
		^this
	}

	// a cable's colour: drawn as stripes over the colour of what it carries
	*cableColorOf { |from, to, param|
		^cableColors.at([this.prKey(from), this.prKey(to), param.asSymbol])
	}

	*setCableColor { |from, to, param, color|
		var k = [this.prKey(from), this.prKey(to), param.asSymbol];
		if (color.isNil) { cableColors.removeAt(k) } { cableColors.put(k, color.asSymbol) };
		this.changed(\colors);
		^this
	}

	// --------------------------------------------------------------- boxes

	// A network box: a coloured box behind the modules, with a name. Moving
	// it in the window moves what is in it. Like a comment it belongs to no
	// Ndef. Answers its id.
	*addBox { |name, rect, color = \teal|
		var id = this.prNextId("box", boxes);
		boxes.put(id, (name: (name ? id).asString, rect: rect ?? { Rect(20, 20, 400, 300) },
			color: (color ? \teal).asSymbol));
		this.changed(\boxes, id);
		^id
	}

	// the boxes in the order they were made: a later one is over an older one
	*boxIds { ^this.prIdOrder(boxes) }

	*boxRect { |id| ^boxes.at(id.asSymbol) !? { |b| b[\rect] } }

	*placeBox { |id, rect|
		boxes.at(id.asSymbol) !? { |b| b[\rect] = rect; this.changed(\boxes, id.asSymbol) };
		^this
	}

	*renameBox { |id, name|
		boxes.at(id.asSymbol) !? { |b| b[\name] = name.asString; this.changed(\boxes, id.asSymbol) };
		^this
	}

	*colorBox { |id, color|
		boxes.at(id.asSymbol) !? { |b| b[\color] = (color ? \teal).asSymbol; this.changed(\boxes, id.asSymbol) };
		^this
	}

	// the box goes; what was in it stays where it is
	*removeBox { |id|
		boxes.removeAt(id.asSymbol) !? { this.changed(\boxes, id.asSymbol) };
		^this
	}

	*clearBoxes {
		boxes.clear;
		this.changed(\boxes);
		^this
	}

	// Ids are a word and a number, higher than any there: box10 comes after
	// box2 here, not before it as symbols sort.
	*prIdNumber { |id| ^id.asString.select(_.isDecDigit).asInteger }

	*prIdOrder { |dict| ^dict.keys.asArray.sort { |a, b| this.prIdNumber(a) <= this.prIdNumber(b) } }

	*prNextId { |word, dict|
		^(word ++ (dict.keys.inject(0, { |m, id| m.max(this.prIdNumber(id)) }) + 1)).asSymbol
	}

	// --------------------------------------------------------------- the code

	// The rack as code you can keep - and run where NdefRack is not
	// installed: plain Ndef code. Every module's source as text - a library
	// module's own, at its width: { |n| { ... } }.value(16) for one playing
	// sixteen voices - each Ndef set to take that width even over one that
	// exists already (reshaping \elastic), then one Ndef(...).set(...) line per
	// module with what its node map holds - numbers, arrays, and Ndef(...)
	// for every cable - then what is playing. Your own Ndefs' sources are
	// written from their text when the interpreter kept it; otherwise named.
	// vbap and ambi carry their speaker setup as numbers. Controller
	// bindings run only where NdefRackKtl is there.
	*patchCode { |names, sources = true|
		var keys, mods, lines, sets, plays, srcs;
		this.prFresh;
		keys = (names ?? { order }).collect { |n| this.prKey(n) };
		mods = keys.collect { |k| modules.at(k) }.reject(_.isNil);
		lines = List["(", "// ndef rack, %".format(Date.getDate.format("%Y-%m-%d %H:%M"))];
		if (sources) {
			srcs = mods.collect { |m| [m, this.prPortableSource(m)] };
			plays = srcs.select { |pair| pair[1].notNil }.collect { |pair| this.prNdefArg(pair[0].name) };
			if (plays.notEmpty) {
				lines.add("// each takes its source's width, also over an Ndef that exists already");
				lines.add("[%].do { |k| Ndef(k).reshaping = \\elastic };".format(plays.join(", ")));
			};
			srcs.do { |pair|
				lines.add(if (pair[1].notNil) {
					"Ndef(%, %);".format(this.prNdefArg(pair[0].name), pair[1])
				} {
					"// % - your own code".format(this.prNdefString(pair[0].name))
				})
			};
		};
		sets = mods.collect { |m|
			var pairs = this.prSettingStrings(m);
			if (pairs.notEmpty) {
				"%.set(%);".format(this.prNdefString(m.name), pairs.join(", "))
			}
		}.reject(_.isNil);
		if (sets.notEmpty) { lines.add(""); lines.addAll(sets) };
		plays = mods.select(_.isMonitoring).collect { |m| this.prPlayString(m) };
		if (plays.notEmpty) { lines.add(""); lines.addAll(plays) };
		// what a controller turns, by the MKtl's name - it need not exist yet;
		// skipped where the rack is not installed
		plays = NdefRackKtl.codeLines(mods.collect(_.name));
		if (plays.notEmpty) {
			lines.add("");
			lines.add("\\NdefRackKtl.asClass !? { |ktl|");
			plays.do { |line| lines.add("\t" ++ line.replace("NdefRackKtl.", "ktl.")) };
			lines.add("};");
		};
		lines.add(")");
		^lines.join("\n")
	}

	// A module's source as code that runs without the rack: a library
	// module's text at its width (NdefRackLib.codeFor), your own function's
	// text when the interpreter kept it - nil when there is none, or more
	// than one slot.
	*prPortableSource { |m|
		var objects = m.proxy.objects;
		var src = objects.at(0) !? (_.source);
		if (objects.size != 1 or: { src.isNil }) { ^nil };
		if (m.kind.notNil) {
			^NdefRackLib.codeFor(src) ?? {
				// no text (a function made in a class file): the rack's way
				if (m.voices > 1) {
					"NdefRackLib(%, %)".format(this.prSym(m.kind), m.voices)
				} {
					"NdefRackLib(%)".format(this.prSym(m.kind))
				}
			}
		};
		^if (src.isKindOf(Function)) { src.def.sourceCode }
	}

	*postPatch { |names, sources = true|
		var code = this.patchCode(names, sources);
		code.postln;
		^code
	}

	// The patch code in a new, unsaved document - what the window's patch as
	// code does. Posted instead when there is no IDE to open a document in.
	*patchDocument { |names, sources = true|
		^this.codeDocument(this.patchCode(names, sources), "ndef rack patch")
	}

	// any code, in a new document of its own
	*codeDocument { |code, title|
		var doc = \Document.asClass;
		if (doc.isNil) {
			code.postln;
			^code
		};
		^doc.new(title ? "ndef rack", code)
	}

	// ------------------------------------------------------------------ patches

	// Next to the class files, so patches travel with the repository however
	// it was installed - the same trick BusManager uses for layouts. Yours go
	// in patches-local/, which git leaves alone; the examples that come with
	// the rack are in patches/.
	*patchDir { ^this.prRepoDir +/+ "patches-local" }
	*examplesDir { ^this.prRepoDir +/+ "patches" }
	*prRepoDir { ^this.filenameSymbol.asString.dirname.dirname }

	*ensurePatchDir {
		var dir = this.patchDir;
		if (File.exists(dir).not) { File.mkdir(dir) };
		^dir
	}

	// Where a patch of that name is read: a path with a slash as given, a
	// bare name yours first, else the example of that name. Saving always
	// goes to yours (prPathIn), so an example is never overwritten.
	*patchPath { |name|
		var mine = this.prPathIn(this.patchDir, name), ex;
		if (name.asString.contains("/") or: { File.exists(mine) }) { ^mine };
		ex = this.prPathIn(this.examplesDir, name);
		^if (File.exists(ex)) { ex } { mine }
	}

	*prPathIn { |dir, name|
		var p = name.asString;
		if (p.contains("/").not) { p = dir +/+ p };
		p = p.standardizePath;
		if (PathName(p).extension.isEmpty and: { File.exists(p).not }) { p = p ++ ".scd" };
		^p
	}

	// what the window's menu offers: yours, newest first, then the examples
	*patches { ^this.prPatchesIn(this.patchDir) ++ this.examplePatches }

	*examplePatches {
		^this.prPatchesIn(this.examplesDir).sort { |a, b| a < b }
	}

	*prPatchesIn { |dir|
		^(dir +/+ "*").pathMatch.select { |p|
			p.endsWith("/").not and: { PathName(p).fileName.beginsWith(".").not }
		}.sort { |a, b| File.mtime(a) > File.mtime(b) }
	}

	// A patch is the modules - by library name, or by source text for an Ndef
	// written in the interpreter - their settings, where they sat, what
	// played, the cables, and what the controllers turn (NdefRackKtl).
	*asDict {
		this.prFresh;
		^(
			version: 1,
			modules: order.collect { |k| this.prModuleDict(modules.at(k)) },
			setups: this.prOwnSetups(order.collect { |k| modules.at(k) })
				.collect { |s| [s.name, s.dirs, s.lfe, s.label] },
			cables: this.cables.select { |c| c.kind == \map }
				.collect { |c| [c.from, c.to, c.param] },
			bends: this.cables.select { |c| c.kind == \map }.collect { |c|
				bends.at([c.from, c.to, c.param]) !? { |b| [c.from, c.to, c.param, b.collect(_.round)] }
			}.reject(_.isNil),
			cableColors: this.cables.select { |c| c.kind == \map }.collect { |c|
				cableColors.at([c.from, c.to, c.param]) !? { |col| [c.from, c.to, c.param, col] }
			}.reject(_.isNil),
			boxes: this.boxIds.collect { |id|
				var b = boxes.at(id), r = b[\rect];
				[b[\name], b[\color], r.left.round, r.top.round, r.width.round, r.height.round]
			},
			comments: this.commentIds.collect { |id|
				var c = comments.at(id), r = c[\rect];
				[c[\text], r.left.round, r.top.round, r.width.round, r.height.round]
			},
			controls: NdefRackKtl.asArray.select { |b| order.includes(b[2]) }
		)
	}

	*savePatch { |name|
		var path;
		this.ensurePatchDir;
		path = this.prPathIn(this.patchDir, name ? "patch");
		File.use(path, "w", { |f| f.write(this.asDict.asCompileString) });
		("NdefRack: saved the patch to %".format(path)).postln;
		this.changed(\patches);
		^path
	}

	// Library modules are made when missing, your own come back from their
	// saved source only when nothing defines them now. Then the values, then
	// the cables - an input the patch leaves empty is unplugged - then what
	// plays. With clear: true, modules the patch does not have are removed.
	*loadPatch { |name, clear = false|
		var path = this.patchPath(name ? "patch");
		var data, wanted, cables;
		if (File.exists(path).not) {
			("NdefRack: no patch at %".format(path)).warn;
			^this
		};
		data = path.load;
		if (data.isNil or: { data[\modules].isNil }) {
			("NdefRack: % is not a patch file".format(path)).warn;
			^this
		};
		wanted = IdentitySet.new;
		// the patch's own speaker setups, before the modules made on them
		(data[\setups] ? []).do { |s| NdefRackSpeakers.add(s[0], s[1], s[2], s[3]) };
		cables = (data[\cables] ? []).collect { |c| c.collect(_.asSymbol) };

		data[\modules].do { |d|
			var key = d[\name].asSymbol;
			wanted.add(key);
			this.prLoadModule(key, d);
			d[\pos] !? { |p| positions.put(key, Point(p[0], p[1])) };
		};
		this.scan;

		data[\modules].do { |d|
			var m = modules.at(d[\name].asSymbol);
			if (m.notNil and: { (d[\settings] ? []).notEmpty }) {
				m.proxy.set(*d[\settings])
			};
			m !? {
				this.prTakeExprs(m.name, d[\exprs]);
				(d[\sliders] ? []).do { |p| this.showVoices(m.name, p) };
			};
		};
		data[\modules].do { |d|
			var m = modules.at(d[\name].asSymbol);
			m !? {
				m.inputs.do { |pair|
					var keep = cables.any { |c|
						c[1] == m.name and: { c[2] == pair[0] }
							and: { c[0] == this.prModuleKey(pair[1]) }
					};
					if (keep.not) { this.disconnect(m.name, pair[0]) };
				}
			};
		};
		cables.do { |c|
			if (modules.at(c[0]).notNil and: { modules.at(c[1]).notNil }) {
				this.connect(c[0], c[1], c[2])
			}
		};
		// the patch's boxes, in place of the ones there were
		data[\boxes] !? { |list|
			this.clearBoxes;
			list.do { |e| this.addBox(e[0], Rect(e[2], e[3], e[4], e[5]), e[1]) };
		};
		// the window's marks are the patch's: what it does not have on its own
		// modules and on the cables into them goes
		[bends, cableColors].do { |dict|
			dict.keys.asArray.do { |k| if (wanted.includes(k[1])) { dict.removeAt(k) } }
		};
		(data[\cableColors] ? []).do { |e| cableColors.put(e[0..2].collect(_.asSymbol), e[3].asSymbol) };
		data[\modules].do { |d|
			var key = d[\name].asSymbol;
			if (d[\color].notNil) { moduleColors.put(key, d[\color].asSymbol) } { moduleColors.removeAt(key) };
			if (d[\compact] == true) { compacted.add(key) } { compacted.remove(key) };
			if ((d[\page] ? 0) > 0) { pageNums.put(key, d[\page]) } { pageNums.removeAt(key) };
			this.changed(\compact, key);
		};
		this.changed(\colors);
		// the patch's comments, in place of the ones there were
		data[\comments] !? { |list|
			this.clearComments;
			list.do { |e| this.addComment(e[0], Point(e[1], e[2]), Point(e[3], e[4])) };
		};
		// where the window bends them
		(data[\bends] ? []).do { |e|
			bends.put(e[0..2].collect(_.asSymbol), e[3].asArray.collect(_.asFloat))
		};
		// the controllers' bindings: each replaces what turned its control
		data[\controls] !? { |list| NdefRackKtl.fromArray(list) };
		data[\modules].do { |d|
			var m = modules.at(d[\name].asSymbol);
			if (m.notNil and: { m.isAudio }) {
				d[\vol] !? { |v| m.proxy.vol = v };
				if (d[\playing] == true) {
					if (m.isMonitoring.not) { this.play(m.name) }
				} {
					if (m.isMonitoring) { m.proxy.stop }
				};
			};
		};

		if (clear) {
			order.copy.do { |k| if (wanted.includes(k).not) { this.remove(k) } }
		};
		("NdefRack: loaded the patch %".format(path)).postln;
		this.changed(\positions);
		this.changed(\bends);
		this.changed(\patches);
		^this
	}

	// ---------------------------------------------------------------------- gui

	*gui { |bounds|
		if (guiObj.isNil) { guiObj = NdefRackGUI(bounds) } { guiObj.front };
		^guiObj
	}

	*prGuiClosed { guiObj = nil }

	// ----------------------------------------------------------------- private

	// The questions about the whole rack look first, watching or not: the
	// poll may be a quarter of a second behind the code that just ran.
	*prFresh { this.scan }

	*prKey { |x|
		^if (x.isKindOf(NodeProxy)) { x.tryPerform(\key) } { x.asSymbol }
	}

	*prShows { |key, px|
		^hidden.includes(key).not
			and: { px.isKindOf(NodeProxy) }
			and: { px.objects.notEmpty or: { px.isNeutral.not } }
	}

	// the module key of a proxy, when it is one of the rack's
	*prModuleKey { |px|
		var key = px.tryPerform(\key);
		^if (key.notNil and: { (modules.at(key) !? (_.proxy)) === px }) { key } { nil }
	}

	*prNoModule { |name|
		("NdefRack: there is no module called %".format(name.asCompileString)).warn;
		^nil
	}

	// A free key: the name itself, then name_2, name_3 ... The name's own
	// _n comes off first, so a copy of osc_2 is osc_3, not osc_2_2 - and a
	// second lfo8 is lfo8_2, never lfo82.
	*prFreeName { |name|
		var base = name.asString;
		var found = base.findRegexp("^(.+)_([0-9]+)$");
		var key, i = 1;
		if (found.notEmpty) { base = found[1][1] };
		key = base.asSymbol;
		while { this.prTaken(key) } {
			i = i + 1;
			key = (base ++ "_" ++ i).asSymbol;
		};
		^key
	}

	*prTaken { |name|
		var px = this.proxySpace !? { |space| space.envir.at(name) };
		^px.notNil and: { px.objects.notEmpty or: { px.isNeutral.not } }
	}

	// What a module's notifications and the poll both come through. A module
	// that was already dropped can still be heard from on its way out.
	*prModuleChanged { |module, what|
		if (modules.at(module.name) !== module) { ^this };
		switch(what,
			\structure, { this.prReread(module, false) },
			// a source of its own - not a rebuild because what it reads moved
			\source, { this.prReread(module, true) },
			\state, {
				module.prReadState;
				this.changed(\state, module.name);
				// a cable made in code takes the voices it brings, a moment later
				this.prFollowSoon(module);
			},
			\monitor, {
				module.prUpdateMonitor;
				this.changed(\monitor, module.name);
			}
		);
	}

	// A new source can be a width given in code: voices no cable explains
	// are kept as given (prAdoptVoices), and what it feeds follows the new
	// width - a moment later, as for a cable made in code.
	*prReread { |module, newSource|
		if (this.prShows(module.name, module.proxy).not) {
			this.prDrop(module.name);
			this.changed(\modules);
			^this
		};
		module.prRead;
		if (newSource) {
			this.prAdoptVoices(module);
			modules.do { |m|
				if (m !== module and: { m.proxy.nodeMap.values.any { |v|
					v === module.proxy or: { m.prSpreadSource(v) === module.proxy } } }) {
					this.prFollowSoon(m)
				}
			};
		};
		this.changed(\module, module.name);
	}

	*prDrop { |key|
		var m = modules.removeAt(key);
		m !? { m.release };
		order.remove(key);
	}

	*prDropAll {
		modules.keys.asArray.do { |key| this.prDrop(key) };
		this.changed(\modules);
	}

	// a name, a list of names, Ndefs: the keys of the modules among them, once each
	*prKeys { |names|
		var keys = [];
		var list = if (names.isKindOf(SequenceableCollection) and: { names.isString.not }) {
			names
		} {
			[names]
		};
		list.do { |n|
			var key = this.prKey(n);
			if (this.at(key).notNil and: { keys.includes(key).not }) { keys = keys.add(key) };
		};
		^keys
	}

	*prRemoveOne { |key|
		var m = modules.at(key);
		// the recorder would go on reading a bus that is freed and given away
		this.prRecordSources(m.proxy).do { |src| this.recorder.remove(src.name) };
		// A writer fades out of its bus, as it does when it moves: cleared at
		// once it was a step there (measured 2026-11-20). Released first, so
		// the unplugging below no longer reaches its synth - with its inputs
		// unmapped on the server, the write would still drop to 0 at once. Its
		// own bus is freed now and the fading synth still writes there for
		// the fade; nothing reads it any more.
		if (m.writesBus) { m.proxy.free(m.proxy.fadeTime, true) };
		this.unplug(key);
		this.prDrop(key);
		m.proxy.clear;
		this.proxySpace !? { |space| space.envir.removeAt(key) };
		positions.removeAt(key);
		bends.keys.asArray.do { |k| if (k[0] == key or: { k[1] == key }) { bends.removeAt(k) } };
		cableColors.keys.asArray.do { |k| if (k[0] == key or: { k[1] == key }) { cableColors.removeAt(k) } };
		moduleColors.removeAt(key);
		compacted.remove(key);
		pageNums.removeAt(key);
		voiceFloors.removeAt(key);
		voiceExprs.removeAt(key);
		voiceSliders.removeAt(key);
		^m
	}

	// ----------------------------------------------------- changing a width

	// A new source for slot index of px - nil: every slot, as source_ does -
	// at whatever width it has. A wider or narrower source moves px to a new
	// bus, and JITLib's own re-send of what reads it then skips some readers
	// and cancels the new synths of others (measured). So the
	// readers are taken out of its hands first and sent again here, in the
	// same moment as the change: not one of them goes silent. after runs
	// between the change and the re-send, when the bus has moved.
	//
	// A reader that px itself reads - a loop, an insert and its effects - is
	// one more case (measured 2026-11-20): px's put loads what it reads into
	// its own bundle, a SynthDefControl remembers the last bundle it was
	// loaded or played in, and its next stop cancels that bundle's synths.
	// So the reader's rebuild takes px's new synth with it, and px is left
	// with an empty group. px is sent once more, in the same moment.
	*prPut { |px, index, source, after|
		var oldBus = px.bus;
		var recorded = this.prRecordSources(px);
		var readers = this.prReaders(px);
		var reshaping = px.reshaping;
		var moved;
		readers.do { |r| px.removeChild(r) };
		px.reshaping = \elastic;
		px.put(index, source);
		px.reshaping = reshaping;
		moved = oldBus.notNil and: { px.bus !== oldBus };
		if (moved) {
			after.value;
			readers.do(_.rebuild);
			if (this.prReadsAny(px, readers)) { px.send };
		};
		readers.do { |r| if ((px.children ? []).includes(r).not) { px.addChild(r) } };
		if (moved) {
			this.prFollowers(px);
			recorded.do { |src| this.prMoveRecordSource(src, px.bus) };
		};
		^px
	}

	// whether px reads one of these proxies, directly or through others: its
	// node map's proxies and those its sources name, and theirs
	*prReadsAny { |px, proxies|
		var seen = IdentitySet[px];
		var todo = [px];
		var next;
		while { todo.notEmpty } {
			next = todo.removeAt(0);
			this.prParents(next).do { |parent|
				if (seen.includes(parent).not) {
					if (proxies.includes(parent)) { ^true };
					seen.add(parent);
					todo = todo.add(parent);
				}
			}
		};
		^false
	}

	*prParents { |px|
		var res = List.new;
		px.nodeMap.values.do { |v|
			v.asArray.do { |x| if (x.isKindOf(NodeProxy)) { res.add(x) } }
		};
		px.objects.do { |obj|
			if (obj.respondsTo(\parents)) { obj.parents.do { |x| if (x.isKindOf(NodeProxy)) { res.add(x) } } }
		};
		^res
	}

	// BusRecorder's sources reading px's bus - the Bus object itself
	*prRecordSources { |px|
		var rec = this.recorder;
		var bus = px !? (_.bus);
		if (rec.isNil or: { bus.isNil }) { ^[] };
		^rec.sources.select { |src| src.kind == \bus and: { src.bus === bus } }
	}

	// The module moved to another bus: its source reads the new one - and a
	// take in progress goes on recording it, as BusRecorder does for a
	// BusManager name that moved. Its own methods for that are private, so
	// they are asked for, and the source made again if they are not there.
	*prMoveRecordSource { |src, bus|
		var rec = this.recorder;
		if (src.respondsTo(\prSetBus).not) { ^rec.addBus(bus, src.name) };
		src.prSetBus(bus);
		rec.tryPerform(\prStartMeter, src);
		rec.tryPerform(\prRetarget, src);
		rec.changed(\sources);
	}

	// px has a new width: every module it is patched into takes its voices
	// again (prFollow) - and, changing, the modules after it, down the chain.
	*prFollowers { |px|
		modules.values.select { |m|
			m.proxy !== px and: { m.canVoice } and: {
				m.proxy.nodeMap.values.any { |v| v === px or: { m.prSpreadSource(v) === px } }
			}
		}.do { |m| this.prFollow(m) }
	}

	// ------------------------------------------------------- following buses

	// BusManager says \buses when a bus is added, taken away, renamed or moved
	// by a boot. Watched from the first bus module on, and only when
	// BusManager is installed: it is looked up by name, never needed.
	*prWatchBuses {
		var bm = \BusManager.asClass;
		if (bm.isNil or: { busWatcher.notNil }) { ^this };
		busWatcher = SimpleController(bm).put(\buses, {
			// it can say so from inside a boot: the modules follow a moment
			// later, out of its way
			{ try { this.prFollowBuses } { |err| ("NdefRack: " ++ err.errorString).warn } }.defer(0.05)
		});
	}

	// Every bus module reads its bus, or writes to it, as BusManager has it
	// now - moved, wider, another rate, back after a while. A name BusManager
	// no longer knows leaves a reader where it was, as BusManager's own
	// modules are; a writer goes to its stand-in, still bound to the name -
	// left where it was, it would write to whatever bus BusManager gives
	// that index next. A bus index of your own is never moved. The window
	// hears of every bus module, for the name on its face.
	*prFollowBuses {
		var bm = \BusManager.asClass;
		if (bm.isNil) { ^this };
		modules.values.select { |m| m.busInfo.notNil }.do { |m|
			var info = m.busInfo;
			var found = info[\name] !? { |n| bm.at(n) };
			case
			{ found.notNil and: {
				[found.index, found.numChannels, found.rate] != [info[\index], info[\channels], info[\rate]]
			} } {
				this.prPutBus(m, NdefRackLib.busReader(m.kind, info[\name], nil, nil, m.voices))
			}
			{ found.isNil and: { info[\name].notNil and: { info[\index].notNil and: { m.writesBus } } } } {
				this.prPutBus(m, NdefRackLib.busReader(m.kind, info[\name], nil, nil, m.voices))
			};
			this.changed(\state, m.name);
		};
	}

	// --------------------------------------------------- following speakers

	// NdefRackSpeakers says \setups when a setup is added, made again with
	// other speakers, or removed. Watched from the first module with speakers
	// on.
	*prWatchSpeakers {
		if (speakerWatcher.notNil) { ^this };
		speakerWatcher = SimpleController(NdefRackSpeakers).put(\setups, {
			{ try { this.prFollowSpeakers } { |err| ("NdefRack: " ++ err.errorString).warn } }.defer(0.05)
		});
	}

	// Every module with speakers pans among those its setup's name stands
	// for now. A name the table no longer has leaves its module on the
	// speakers it had. The window hears of every one, for the name on its
	// face.
	*prFollowSpeakers {
		modules.values.select { |m| m.speakerInfo.notNil }.do { |m|
			var info = m.speakerInfo;
			var found = NdefRackSpeakers.at(info[\name]);
			if (found.notNil and: { found !== info[\setup] }) {
				this.prPutSpeakers(m, NdefRackLib.speakerFunc(m.kind, found, m.voices))
			};
			this.changed(\state, m.name);
		};
	}

	// a module's new source, as wide as its speaker setup
	*prPutSpeakers { |m, func|
		var info = NdefRackLib.speakersFor(func);
		if (func.isNil or: { func === (m.proxy.objects.at(0) !? (_.source)) }) { ^m };
		info !? { this.prRelayMonitor(m, info[\setup].numChannels) };
		this.prPut(m.proxy, 0, func);
		^m
	}

	// whether a module's source pans among the speakers asked for: that name,
	// and the speakers the table has under it now
	*prHasSpeakers { |src, speakers|
		var info = NdefRackLib.speakersFor(src);
		var name = (speakers ?? { NdefRackSpeakers.default }).asSymbol;
		var found = NdefRackSpeakers.at(name);
		^info.notNil and: { info[\name] == name } and: { found.isNil or: { found === info[\setup] } }
	}

	// what the export writes after the module's name: speakers: \octo -
	// nothing for the default
	*prSpeakerArgs { |m|
		var name = m.speakers;
		^if (name.isNil or: { name == NdefRackSpeakers.default }) { "" } {
			", speakers: %".format(this.prSym(name))
		}
	}

	// the speaker setups these modules use that do not come with the rack
	*prOwnSetups { |mods|
		var res = [];
		mods.do { |m|
			var setup = m !? { m.speakerInfo } !? { |i| i[\setup] };
			if (setup.notNil and: { setup.builtin.not } and: { res.includes(setup).not }) {
				res = res.add(setup)
			};
		};
		^res
	}

	// a bus module's new source, at whatever width and rate its bus has
	*prPutBus { |m, func|
		var info = NdefRackLib.busFor(func);
		if (func.isNil or: { func === (m.proxy.objects.at(0) !? (_.source)) }) { ^m };
		info !? { this.prRelayMonitor(m, info[\channels]) };
		this.prPut(m.proxy, 0, func);
		^m
	}

	// a bus given by its BusManager name, not by an index or a Bus
	*prIsBusName { |bus| ^bus.isKindOf(Symbol) or: { bus.isKindOf(String) } }

	// whether a bus module's source is on the bus asked for: the same name,
	// wherever BusManager has it now - or the same index, width and rate
	*prReadsBus { |kind, src, bus, channels, rate|
		var info = NdefRackLib.busFor(src);
		^case
		{ info.isNil } { false }
		{ bus.isNil } { info[\name].isNil and: { info[\index].isNil } }
		{ this.prIsBusName(bus) } { info[\name] == bus.asSymbol }
		{ src === NdefRackLib.busReader(kind, bus, channels, rate, NdefRackLib.voicesFor(src) ? 1) }
	}

	// what the export writes after the module's name: bus: \reverb - or the
	// index, with its width and rate when they are not 1 and audio
	*prBusArgs { |m|
		var info = m.busInfo;
		var str;
		^case
		{ info.isNil } { "" }
		{ info[\name].notNil } { ", bus: %".format(this.prSym(info[\name])) }
		{ info[\index].notNil } {
			str = ", bus: %".format(info[\index]);
			if (info[\channels] != 1) { str = str ++ ", channels: %".format(info[\channels]) };
			if (info[\rate] != \audio) { str = str ++ ", rate: %".format(this.prSym(info[\rate])) };
			str
		}
		{ "" }
	}

	// BusManager's buses are numbers on BusManager's server: on another one
	// they are somebody else's buses
	*prCheckBusServer { |kind|
		var bm = \BusManager.asClass;
		if (bm.notNil and: { bm.server !== this.server }) {
			("NdefRack: BusManager keeps its buses on %, the rack is on % - a bus module %"
				" those numbers on %".format(bm.server.name, this.server.name,
					kind !? { NdefRackLib.busVerb(kind) } ? "reads", this.server.name)).warn;
		};
	}

	// What reads px: JITLib's children - its taps and the proxies whose node
	// map holds it - and the proxies holding it in an array, a mono cable
	// spread over voices, which JITLib does not count among them.
	*prReaders { |px|
		var res = (px.children ? []).copy;
		this.proxySpace !? { |space|
			space.envir.do { |other|
				if (other.isKindOf(NodeProxy) and: { res.includes(other).not } and: {
					other.nodeMap.values.any { |v| v.isKindOf(SequenceableCollection) and: { v.includes(px) } }
				}) {
					res = res.add(other)
				}
			}
		};
		^res
	}

	// px has a new width: the cables from it into controls with a value per
	// voice made again for it - spread over the voices from a mono px, voice
	// to voice from a wider one. The node maps only, nothing sent: prPut
	// sends every reader again straight after.
	*prRepatchReaders { |px|
		modules.do { |m|
			if (m.proxy !== px) {
				m.params.do { |p|
					var v = m.proxy.nodeMap.at(p[\name]);
					var want;
					if (v === px or: { m.prSpreadSource(v) === px }) {
						want = m.cableFor(p[\name], px);
						if (this.prSame(want, v).not) { m.proxy.nodeMap.set(p[\name], want) };
					}
				}
			}
		}
	}

	// Before a module goes to n voices, its node map made ready for n: a
	// control's value on every voice, a row of sliders cut, or lengthened with
	// its last value, a mono cable spread over every voice - and back to the
	// one value, the plain cable, for one voice. Sent now, the old synth takes
	// what fits; the new one starts from all of it.
	*prWidenSettings { |m, n|
		var pairs = [];
		NdefRackLib.voiceKeys(m.kind).do { |key|
			var v = m.proxy.nodeMap.at(key);
			var src = m.prSpreadSource(v) ?? { if (v.tryPerform(\nodeMapMapsToControl) == true) { v } };
			var want = case
				{ src.notNil } {
					if (n > 1 and: { src.numChannels == 1 }) { Array.fill(n, { src }) } { src }
				}
				{ v.isNumber } { if (n > 1) { v ! n } { v } }
				{ m.prIsValue(v) } {
					if (n > 1) { v.asArray.extend(n, v.asArray.last) } { v.asArray.first }
				};
			if (want.notNil and: { this.prSame(want, v).not }) { pairs = pairs ++ [key, want] };
		};
		if (pairs.notEmpty) { m.proxy.set(*pairs) };
	}

	// A playing module about to change width: the monitor's layout for the
	// new width goes in first, so JITLib - restarting the monitor along with
	// the change - plays every channel where it belongs from the start.
	*prRelayMonitor { |m, width|
		var px = m.proxy;
		var layout;
		if (m.isMonitoring.not or: { width == px.numChannels } or: { this.prLayoutFits(px).not }) { ^this };
		layout = this.prLayoutFor(m, width);
		px.monitor.outs = layout[0];
		px.monitor.amps = layout[1];
	}

	// The monitor plays every channel somewhere already - the rack's way or yours.
	*prLayoutFits { |px|
		var outs = px.monitor !? { |mon| mon.outs };
		^outs.notNil and: {
			outs.size == px.numChannels or: { px.numChannels == 1 and: { outs.size == 2 } }
		}
	}

	// Where a module's channels go when it is that wide: a module panning
	// among speakers has a channel per speaker, and each goes to its own
	// output when the server has that many - never past them, where the
	// inputs and other modules' buses lie. Anything else, the stereo pair.
	*prLayoutFor { |m, width|
		^if (this.prToSpeakers(m, width)) {
			[(0 .. width - 1), 1 ! width]
		} {
			this.prMonitorLayout(width)
		}
	}

	*prToSpeakers { |m, width|
		^m.canSpeakers and: { width > 2 } and: { width <= this.server.options.numOutputBusChannels }
	}

	// Where n channels go on the stereo pair, and how loud: [outs, amps]. One,
	// on both sides; two, left and right; more - voices - spread across the
	// pair as Splay spreads them, equal power, levelled by 1 / sqrt(n).
	*prMonitorLayout { |n|
		var comp;
		^case
		{ n <= 1 } { [[0, 1], [1]] }
		{ n == 2 } { [[0, 1], [1, 1]] }
		{
			comp = n.sqrt.reciprocal;
			[
				Array.fill(n, { [0, 1] }),
				Array.fill(n, { |i|
					var a = i / (n - 1) * 0.5pi;
					([a.cos, a.sin] * comp).round(0.000001)
				})
			]
		}
	}

	// the same proxy, number, or array of them - == would ask the proxies
	*prSame { |a, b|
		^(a === b)
		or: { a.isNumber and: { b.isNumber } and: { a == b } }
		or: {
			a.isKindOf(SequenceableCollection) and: { b.isKindOf(SequenceableCollection) }
				and: { a.size == b.size } and: { a.every { |x, i| this.prSame(x, b[i]) } }
		}
	}

	// What it takes to make these modules again, and their cables: those
	// between them, and with outside: true those to and from anything else.
	*prSnapshot { |keys, outside = false|
		var inside = { |k| keys.includes(k) };
		^(
			modules: keys.collect { |k| this.prModuleSnapshot(modules.at(k)) },
			cables: this.cables.select { |c|
				c[\kind] == \map and: {
					if (outside) {
						inside.(c[\from]) or: { inside.(c[\to]) }
					} {
						inside.(c[\from]) and: { inside.(c[\to]) }
					}
				}
			}.collect { |c|
				var k = [c[\from], c[\to], c[\param]];
				k ++ [bends.at(k), cableColors.at(k)]
			}
		)
	}

	// The sources slot by slot, not a library key: a copy of your own Ndef is
	// as much a copy as one of a library module, and the same function object
	// keeps a library copy saying where it came from.
	*prModuleSnapshot { |m|
		var px = m.proxy;
		^(
			name: m.name,
			sources: px.objects.indices.collect { |i| [i, px.objects.at(i).source] },
			values: m.copyValues,
			pos: positions.at(m.name),
			color: moduleColors.at(m.name),
			compact: compacted.includes(m.name),
			page: pageNums.at(m.name),
			voiceFloor: voiceFloors.at(m.name),
			exprs: this.prExprPairs(m),
			voiceSliders: voiceSliders.at(m.name) !? { |set| set.asArray.sort },
			rate: m.rate,
			numChannels: m.numChannels,
			playing: m.isMonitoring,
			vol: px.vol,
			outs: px.monitor !? { |mon| mon.outs },
			amps: px.monitor !? { |mon| mon.amps },
			recorded: this.recordSource(m.name) !? { |src| src.armed }
		)
	}

	// Make a snapshot's modules: every Ndef first, on a bus of the same shape,
	// then the values, then the cables between them - names.at(old) is the new
	// key, and an end that is not in the snapshot keeps its name and is only
	// patched if it still exists. Answers old name -> new name.
	*prRealize { |snap, offset, keepNames = false|
		var names = IdentityDictionary.new;
		snap[\modules].do { |d|
			var key = if (keepNames and: { this.prTaken(d[\name]).not }) {
				d[\name]
			} {
				this.prFreeName(d[\name])
			};
			var px = this.ndef(key);
			names.put(d[\name], key);
			if (d[\rate].notNil) { px.mold(d[\numChannels], d[\rate]) };
			d[\sources].do { |pair| px.put(pair[0], pair[1]) };
			if (d[\values].notEmpty) { px.set(*d[\values]) };
			d[\pos] !? { |p| positions.put(key, p + offset) };
			d[\color] !? { |col| moduleColors.put(key, col) };
			if (d[\compact] == true) { compacted.add(key) };
			d[\page] !? { |pg| pageNums.put(key, pg) };
		};
		this.scan;
		// after the scan, which takes any width it finds for given
		snap[\modules].do { |d|
			this.prSetFloor(names.at(d[\name]), d[\voiceFloor]);
			this.prTakeExprs(names.at(d[\name]), d[\exprs]);
			(d[\voiceSliders] ? []).do { |p| this.showVoices(names.at(d[\name]), p) };
		};
		snap[\cables].do { |c|
			var from = names.at(c[0]) ? c[0];
			var to = names.at(c[1]) ? c[1];
			if (modules.at(from).notNil and: { modules.at(to).notNil }) {
				this.connect(from, to, c[2]);
				// bent and coloured as it was, the bend where the copies are
				c[3] !? { |b|
					bends.put([from, to, c[2]],
						if (b.size == 1) { b + offset.x } { b + [offset.x, offset.y, offset.x] })
				};
				c[4] !? { |col| cableColors.put([from, to, c[2]], col) };
			};
		};
		this.changed(\bends);
		this.changed(\colors);
		// a copy whose wide cable stayed behind goes back to its own voices
		names.do { |key| this.prFollow(modules.at(key)) };
		^names
	}

	*prTopLeft { |snap|
		var ps = snap[\modules].collect { |d| d[\pos] }.reject(_.isNil);
		if (ps.isEmpty) { ^Point(0, 0) };
		^Point(ps.collect(_.x).minItem, ps.collect(_.y).minItem)
	}

	*prLoadModule { |key, d|
		var px = this.proxySpace !? { |space| space.envir.at(key) };
		var defined = px.notNil and: { px.objects.notEmpty };
		var kind = d[\kind] !? (_.asSymbol);
		var voices = (d[\voices] ? 1).asInteger;
		var bus = d[\bus] !? { |b| if (b.isNumber) { b.asInteger } { b.asSymbol } };
		var channels = d[\channels], rate = d[\rate] !? (_.asSymbol);
		var speakers = d[\speakers] !? (_.asSymbol);
		var src = if (defined) { px.objects.at(0) !? (_.source) };
		var current = src !? { NdefRackLib.keyFor(src) };
		case
		{ kind.notNil } {
			if (NdefRackLib.includes(kind)) {
				case
				{ current != kind } { this.make(kind, key, voices, bus, channels, rate, speakers) }
				{ NdefRackLib.canSpeakers(kind) and: { this.prHasSpeakers(src, speakers).not } } {
					this.setSpeakers(key, speakers)
				}
				{ NdefRackLib.canBus(kind) and: { this.prReadsBus(kind, src, bus, channels, rate).not } } {
					this.setBus(key, bus, channels, rate)
				}
				{ (NdefRackLib.voicesFor(src) ? 1) != voices } { this.setVoices(key, voices) };
			} {
				("NdefRack: % needs the library module %, which is not there"
					.format(key, kind)).warn;
			}
		}
		{ defined.not and: { d[\source].notNil } } {
			this.ndef(key, d[\source].interpret);
			("NdefRack: % is back, from the source saved in the patch".format(key)).postln;
		}
		{ defined.not } {
			("NdefRack: % is not defined - define it and load the patch again"
				.format(key)).postln;
		};
	}

	*prModuleDict { |m|
		var d = (name: m.name, settings: m.settings, playing: m.isMonitoring);
		var pos = positions.at(m.name);
		var objects = m.proxy.objects;
		pos !? { d[\pos] = [pos.x.round, pos.y.round] };
		moduleColors.at(m.name) !? { |col| d[\color] = col };
		if (compacted.includes(m.name)) { d[\compact] = true };
		pageNums.at(m.name) !? { |pg| d[\page] = pg };
		if (m.isAudio) { d[\vol] = m.proxy.vol };
		if (m.kind.notNil) {
			d[\kind] = m.kind;
			// the voices given it; the cables, made again, bring the rest
			if (m.canVoice and: { this.voiceFloor(m.name) > 1 }) { d[\voices] = this.voiceFloor(m.name) };
			this.prExprPairs(m) !? { |pairs| d[\exprs] = pairs };
			voiceSliders.at(m.name) !? { |set| d[\sliders] = set.asArray.sort };
			m.speakers !? { |name| if (name != NdefRackSpeakers.default) { d[\speakers] = name } };
			m.bus !? { |b|
				d[\bus] = b;
				if (b.isNumber) {
					d[\channels] = m.busInfo[\channels];
					d[\rate] = m.busInfo[\rate];
				};
			};
		} {
			// a function written in the interpreter keeps its text, so the
			// patch can bring the module back; one with filters or slots is
			// more than one function, and stays your code's business
			if (objects.size == 1) {
				objects.at(0) !? { |obj|
					var src = obj.source;
					if (src.isKindOf(Function) and: { src.def.sourceCode.notNil }) {
						d[\source] = src.def.sourceCode
					};
				};
			};
		};
		^d
	}

	*prSettingStrings { |m|
		var res = [];
		m.params.do { |p|
			var src = m.source(p[\name]);
			var v = m.proxy.nodeMap.at(p[\name]);
			case
			{ src.notNil } {
				// a mono cable spread over the voices is written as it is made
				res = res.add(this.prSym(p[\name]) ++ ", " ++ this.prSourceString(src)
					++ if (m.isSpread(p[\name])) { " ! %".format(v.size) } { "" })
			}
			// a trigger's number is not written: evaluated, it would fire
			{ m.prIsValue(v) and: { p[\type] != \trigger } } {
				res = res.add(this.prSym(p[\name]) ++ ", " ++ this.prValueString(v))
			};
		};
		^res
	}

	// Where the monitor goes, said the shortest way that says it: a mono
	// module the rack played on both sides is .play(numChannels: 2), not .play.
	*prPlayString { |m|
		var px = m.proxy;
		var outs = px.monitor !? { |mon| mon.outs };
		var amps = px.monitor !? { |mon| mon.amps };
		var str = this.prNdefString(m.name);
		var args;
		// levels of their own - voices spread across the pair - take playN to say
		if (outs.notNil and: { amps.notNil } and: { amps.asArray.flat.every { |a| a == 1 }.not }) {
			^"%.playN(%, %);".format(str, this.prValueString(outs), this.prValueString(amps))
		};
		if (outs.isNil or: { outs.asArray == (0 .. px.numChannels - 1) }) { ^str ++ ".play;" };
		outs = outs.asArray;
		if (outs == (outs[0] .. outs[0] + outs.size - 1)) {
			args = [
				if (outs[0] != 0) { "out: %".format(outs[0]) },
				if (outs.size != px.numChannels) { "numChannels: %".format(outs.size) }
			].reject(_.isNil);
			^"%.play(%);".format(str, args.join(", "))
		};
		^"%.playN(%);".format(str, outs.asCompileString)
	}

	*prSourceString { |px|
		var key = this.prModuleKey(px);
		^if (key.notNil) { this.prNdefString(key) } { px.asCompileString }
	}

	// a number, or an array of them; the same value n times is written v ! n
	*prValueString { |v|
		^case
		{ v.isKindOf(SequenceableCollection) and: { v.size > 1 } and: { v.every { |x| x == v.first } } } {
			"% ! %".format(this.prValueString(v.first), v.size)
		}
		{ v.isKindOf(SequenceableCollection) } {
			"[" ++ v.collect { |x| this.prValueString(x) }.join(", ") ++ "]"
		}
		{ v.isInteger } { v.asString }
		{ v.asStringPrec(6) }
	}

	// Ndef(\osc) on the default server, Ndef(\osc -> \other) on any other
	*prNdefString { |key| ^"Ndef(%)".format(this.prNdefArg(key)) }

	*prNdefArg { |key|
		^if (this.server === (Ndef.defaultServer ? Server.default)) {
			this.prSym(key)
		} {
			"% -> %".format(this.prSym(key), this.prSym(this.server.name))
		}
	}

	// \name when it can be written that way, 'name' when it cannot
	*prSym { |sym|
		var str = sym.asString;
		^if (str.notEmpty and: { str[0].isAlpha }
			and: { str.every { |c| c.isAlphaNum or: { c == $_ } } }) {
			"\\" ++ str
		} {
			sym.asSymbol.asCompileString
		}
	}
}
