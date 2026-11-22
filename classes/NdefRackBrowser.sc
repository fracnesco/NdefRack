// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackBrowser - the module library down the left of the rack window
//
//   +--------------------+
//   | modules        [<] |      the toolbar's modules button, cmd-b:
//   | [search        ]   |      shown or folded away
//   | [by category  v]   |      cmd-f: the search, the panel shown
//   | v filters      12  |      a heading: click folds it
//   |   lpf          x2  |      x2: two of them on the rack
//   |   hpf              |
//   | > drums         7  |
//   +--------------------+
//
// Every module NdefRackLib has, sorted by category, by name, by rate
// (audio or control), by the folder it was loaded from, or the ones made
// most recently first - and narrowed to the names holding what is typed in
// the search. Double click a name, or drag it onto the rack, to make the
// module: dropped, where it lands; double clicked, in a free spot in view.
// In the search, up and down move along the names, return makes the one
// marked, escape empties the search.
//
// It keeps no patch either: what it lists is the library, read again
// when NdefRackLib says \library; the counts are the window's modules.
// The sort, whether it is shown and what was made recently are class side,
// so a window opened again finds them as they were.
// =====================================================================

NdefRackBrowser {
	classvar <>shown = true, <>sortBy = \category, <>width = 212;
	classvar <recent, rates, rateTask;	// rateTask: a Task, so a cmd-period stops it
	classvar <sorts, sortNames;

	var <gui, <view, <field, <menu, scroll, <list, foldButton;
	var <rows, <filter = "", <current, folded;
	var press, <dragging = false, dirty = true, libCtrl;

	*initClass {
		recent = List.new;
		rates = IdentityDictionary.new;
		sorts = [\category, \name, \rate, \folder, \recent];
		sortNames = ["by category", "by name", "by rate", "by folder", "recent first"];
	}

	*new { |gui| ^super.new.init(gui) }

	// a module made from the window - this panel or the + module menu - goes
	// to the head of the recent ones
	*noteUsed { |key|
		key = key.asSymbol;
		recent.remove(key);
		recent.addFirst(key);
		if (recent.size > 24) { recent.pop };
	}

	rowH { ^18 }

	headH { ^22 }

	init { |argGui|
		var font, c = NdefRackGUI;
		gui = argGui;
		font = gui.font;
		folded = IdentitySet.new;
		field = TextField().font_(font).background_(c.colTrack).stringColor_(c.colText)
			.keyDownAction_({ |v, char, mod, unicode, keycode, key| this.prFieldKey(key) })
			.keyUpAction_({ |v| this.filter_(v.string) });
		try { field.setProperty(\placeholderText, "search by name") };
		menu = PopUpMenu().font_(font).canFocus_(false).items_(sortNames)
			.value_(sorts.indexOf(sortBy) ? 0)
			.action_({ |v| this.sortBy_(sorts[v.value ? 0]) });
		scroll = ScrollView().hasBorder_(false).hasHorizontalScroller_(false)
			.background_(c.colBg);
		list = UserView(scroll, Rect(0, 0, width - 16, 100))
			.background_(c.colBg)
			.drawFunc_({ this.prDraw })
			.mouseDownAction_({ |uv, x, y, mod, button, clicks| this.prMouseDown(x @ y, mod, clicks) })
			.mouseMoveAction_({ |uv, x, y, mod| this.prMouseMove(x @ y, mod) })
			.mouseUpAction_({ |uv, x, y, mod| this.prMouseUp(x @ y, mod) });
		foldButton = Button().fixedWidth_(22).fixedHeight_(20).font_(font).canFocus_(false)
			.states_([["<", c.colText, c.colPanel]])
			.action_({ gui.showBrowser(false) });
		view = View().fixedWidth_(width).layout_(VLayout(
			HLayout(
				StaticText().string_("modules").font_(gui.boldFont).stringColor_(c.colText),
				nil,
				foldButton
			),
			field,
			menu,
			[scroll, stretch: 1]
		).margins_([0, 0, 2, 0]).spacing_(4));
		gui.hints.attach(foldButton, "fold the panel away - cmd-b, or the modules button, brings it back");
		gui.hints.attach(field, "type part of a name: only the modules whose names hold it - up and down move, return makes the marked one, escape empties it");
		gui.hints.attach(menu, "how the modules are listed: by category, by name, by rate - audio or control -, by the folder they come from, or the ones made recently first");
		gui.hints.attach(list, { |v, x, y| this.hintAt(x @ y) });
		view.visible = shown;
		libCtrl = SimpleController(NdefRackLib).put(\library, {
			NdefRackBrowser.prLibraryChanged;
			dirty = true
		});
		this.prBuildRows;
	}

	remove {
		libCtrl !? { libCtrl.remove; libCtrl = nil };
	}

	// ------------------------------------------------------------- the list

	filter_ { |str|
		str = (str ? "").asString;
		if (str == filter) { ^this };
		filter = str;
		this.prBuildRows;
	}

	sortBy_ { |sym|
		sortBy = sym.asSymbol;
		if (menu.value != (sorts.indexOf(sortBy) ? 0)) { menu.value = sorts.indexOf(sortBy) ? 0 };
		this.prBuildRows;
	}

	sortBy { ^sortBy }

	// the keys listed, in their order, the folded ones too
	keys { ^this.prGroups.collect { |g| g[1] }.flat }

	// the modules rows only: what return and the arrows walk along
	shownKeys { ^rows.select { |r| r[\type] == \module }.collect { |r| r[\key] } }

	current_ { |key|
		current = key;
		this.prReveal;
		list.refresh;
	}

	fold { |group, flag = true|
		if (flag) { folded.add(group) } { folded.remove(group) };
		this.prBuildRows;
	}

	isFolded { |group| ^folded.includes(group) }

	// the module key, or the heading's group, under a point of the list
	rowAt { |point|
		var y = 0;
		rows.do { |r|
			var h = this.prRowH(r);
			if (point.y >= y and: { point.y < (y + h) }) { ^r };
			y = y + h;
		};
		^nil
	}

	rowTop { |key|
		var y = 0;
		rows.do { |r|
			if (r[\type] == \module and: { r[\key] == key }) { ^y };
			y = y + this.prRowH(r);
		};
		^nil
	}

	hintAt { |point|
		var r = this.rowAt(point);
		^r !? {
			if (r[\type] == \head) {
				"% - % module% - click to %".format(r[\title], r[\count], if (r[\count] == 1) { "" } { "s" },
					if (this.isFolded(r[\group])) { "show them" } { "fold them" })
			} {
				var help = NdefRackLib.help(r[\key]) ? "";
				"%: % - double click, or drag it onto the rack, to make one".format(r[\key],
					if (help.isEmpty) { "a module of " ++ (NdefRackLib.entry(r[\key]) !? (_[\category])) } { help })
			}
		} ? "every module in the library - double click one, or drag it onto the rack"
	}

	// make the marked module in a free spot of what is seen
	makeCurrent {
		current !? { |key| gui.prMakeModule(key, nil) }
	}

	// --------------------------------------------------------------- mouse

	prMouseDown { |point, mod, clicks = 1|
		var r = this.rowAt(point);
		press = nil;
		if (r.isNil) { ^this };
		if (r[\type] == \head) {
			this.fold(r[\group], this.isFolded(r[\group]).not);
			^this
		};
		this.current = r[\key];
		if (clicks >= 2) { this.makeCurrent; ^this };
		press = (key: r[\key], point: point);
	}

	prMouseMove { |point, mod|
		press !? {
			if (dragging.not and: { (point - press[\point]).rho > 4 }) { dragging = true };
			if (dragging) { gui.prLibraryDrag(press[\key], gui.prRackPointAt(list.mapToGlobal(point))) };
		}
	}

	prMouseUp { |point, mod|
		if (dragging) { gui.prLibraryDrop(press[\key], gui.prRackPointAt(list.mapToGlobal(point))) };
		dragging = false;
		press = nil;
	}

	// keys in the search: up and down walk the names, return makes, escape
	// empties; everything else is typing
	prFieldKey { |key|
		var keys = this.shownKeys, i;
		case
		{ key == 16r01000013 or: { key == 16r01000015 } } {
			if (keys.notEmpty) {
				i = keys.indexOf(current);
				i = if (i.isNil) { 0 } { (i + if (key == 16r01000013) { -1 } { 1 }).clip(0, keys.size - 1) };
				this.current = keys[i];
			};
		}
		{ key == 16r01000004 or: { key == 16r01000005 } } {
			if (current.isNil or: { keys.includes(current).not }) { current = keys.first };
			this.makeCurrent;
		}
		{ key == 16r01000000 } {
			field.string = "";
			this.filter_("");
			gui.canvas.focus(true);
		}
		{ ^nil };
		^true
	}

	// --------------------------------------------------------------- frame

	// from the window's frame: the library reloaded, the rack changed (the
	// counts), the rates worked out so far
	prFrame { |rackChanged = false|
		if (dirty or: { rackChanged }) { this.prBuildRows };
	}

	// ------------------------------------------------------------- private

	// [[group, keys, title], ...] in the order shown, filtered by the search
	prGroups {
		var keys = NdefRackLib.keys, groups, entry = { |k| NdefRackLib.entry(k) };
		if (filter.notEmpty) { keys = keys.select { |k| k.asString.containsi(filter) } };
		^switch(sortBy,
			\name, { [[\all, keys, nil]] },
			\rate, {
				this.prStartRates;
				[[\audio, "audio"], [\control, "control"], [\none, "does not build"], [nil, "working it out"]]
					.collect { |pair| [pair[0] ? \pending, keys.select { |k| rates[k] == pair[0] }, pair[1]] }
			},
			\folder, {
				groups = Dictionary.new;
				keys.do { |k| var f = this.prFolderOf(entry.(k)[\path]); groups[f] = (groups[f] ? []).add(k) };
				groups.keys.asArray.sort { |a, b| this.prFolderRank(a) <= this.prFolderRank(b) }
					.collect { |f| [f.asSymbol, groups[f], f] }
			},
			\recent, {
				[[\recent, recent.select { |k| keys.includes(k) }.asArray, "made recently"],
					[\rest, keys.reject { |k| recent.includes(k) }, "the rest"]]
			},
			// by category, OF's categories after the rest
			{
				NdefRackLib.categories.sort { |a, b|
					var oa = a.asString.beginsWith("of"), ob = b.asString.beginsWith("of");
					if (oa == ob) { a.asString <= b.asString } { ob }
				}.collect { |cat|
					[cat, keys.select { |k| entry.(k)[\category] == cat },
						if (cat.asString.beginsWith("of")) { "of " ++ cat.asString.drop(2) } { cat.asString }]
				}
			}
		).select { |g| g[1].notEmpty }
	}

	prBuildRows {
		var counts = IdentityDictionary.new, out = [], showSide;
		dirty = false;
		NdefRack.modules.do { |m| m.kind !? { |k| counts[k] = (counts[k] ? 0) + 1 } };
		showSide = sortBy != \category;
		this.prGroups.do { |g|
			var group = g[0], keys = g[1], title = g[2];
			title !? { out = out.add((type: \head, group: group, title: title, count: keys.size)) };
			// a search shows what it found, folded or not
			if (title.isNil or: { filter.notEmpty } or: { folded.includes(group).not }) {
				keys.do { |k|
					out = out.add((type: \module, key: k, count: counts[k] ? 0,
						side: if (showSide) { NdefRackLib.entry(k) !? { |e| e[\category].asString } }))
				}
			}
		};
		rows = out;
		if (filter.notEmpty and: { this.shownKeys.includes(current).not }) { current = this.shownKeys.first };
		this.prResize;
		list.refresh;
	}

	prRowH { |r| ^if (r[\type] == \head) { this.headH } { this.rowH } }

	prResize {
		var h = rows.sum { |r| this.prRowH(r) } ? 0;
		var w = width - 16;
		h = h.max(40);
		if (list.bounds.height != h or: { list.bounds.width != w }) { list.bounds = Rect(0, 0, w, h) };
	}

	// the marked row scrolled into what is seen
	prReveal {
		var top = this.rowTop(current), o, sh;
		if (top.isNil) { ^this };
		o = scroll.visibleOrigin;
		sh = scroll.bounds.height;
		if (top < o.y) { scroll.visibleOrigin = Point(0, top) };
		if ((top + this.rowH) > (o.y + sh)) { scroll.visibleOrigin = Point(0, top + this.rowH - sh) };
	}

	prDraw {
		var c = NdefRackGUI, font = gui.font, small = Font(Font.defaultSansFace, 10);
		var w = list.bounds.width, y = 0;
		rows.do { |r|
			var h = this.prRowH(r);
			if (r[\type] == \head) {
				Pen.fillColor = c.colTitle;
				Pen.fillRect(Rect(0, y + 2, w, h - 2));
				Pen.stringLeftJustIn(
					(if (filter.isEmpty and: { folded.includes(r[\group]) }) { "▸ " } { "▾ " }) ++ r[\title],
					Rect(6, y + 2, w - 40, h - 2), gui.boldFont, c.colText);
				Pen.stringRightJustIn(r[\count].asString, Rect(w - 40, y + 2, 34, h - 2), small, c.colDim);
			} {
				if (r[\key] == current) {
					Pen.fillColor = c.colPanel;
					Pen.fillRect(Rect(0, y, w, h));
					Pen.fillColor = c.colAccent;
					Pen.fillRect(Rect(0, y, 2, h));
				};
				Pen.stringLeftJustIn(r[\key].asString, Rect(16, y, w - 70, h), font, c.colText);
				Pen.stringRightJustIn(
					[r[\side], if (r[\count] > 0) { "x" ++ r[\count] }].select(_.notNil).join("  "),
					Rect(w - 100, y, 94, h), small, if (r[\count] > 0) { c.colAccent } { c.colDim });
			};
			y = y + h;
		};
	}

	// modules/, modules-local/ and its folders, an added folder, or code
	prFolderOf { |path|
		var dir, lib = NdefRackLib.dir, local = lib.dirname +/+ "modules-local";
		path ?? { ^"added from code" };
		dir = path.dirname;
		^case
		{ dir == lib } { "modules" }
		{ dir == local } { "modules-local" }
		{ dir.beginsWith(local ++ "/") } { "modules-local/" ++ dir.drop(local.size + 1) }
		{ dir.beginsWith("~".standardizePath) } { "~" ++ dir.drop("~".standardizePath.size) }
		{ dir }
	}

	prFolderRank { |f|
		^case
		{ f == "modules" } { "0" }
		{ f.beginsWith("modules-local") } { "1" ++ f }
		{ f == "added from code" } { "3" }
		{ "2" ++ f }
	}

	// the rate of every module, a few a frame on AppClock: each one is a
	// SynthDef built in the language (NdefRackLib.shape, kept after)
	prStartRates {
		var todo;
		if (rateTask.notNil and: { rateTask.isPlaying }) { ^this };
		rateTask = nil;
		todo = NdefRackLib.keys.reject { |k| rates[k].notNil };
		if (todo.isEmpty) { ^this };
		rateTask = Task {
			todo.do { |k, i|
				rates[k] = NdefRackLib.shape(k) !? { |s| s[\rate] } ? \none;
				if (i % 6 == 5) { dirty = true; 0.02.wait };
			};
			dirty = true;
		}.play(AppClock);
	}

	*prLibraryChanged {
		rates = IdentityDictionary.new;
		rateTask !? { rateTask.stop; rateTask = nil };
	}
}
