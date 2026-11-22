// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackModuleView - one module in the window
//
//   +--------------------------------------------+
//   |[>] flt                      lpf         [x]|  title: play, name, kind, delete
//   o  in  <- osc                 ..  ar 1      o   main row: main input | output
//   |--------------------------------------------|  a gap between bands
//   <> [freq ==========|---------     1.2 kHz]   |  a bar: name, fill, value
//   <> [oct  ------|====------       +0.50 oct]  |  bipolar: fills from zero
//   <> [res <- lfo  : ~~~|~~~ :           0.41]  |  patched: the live value
//   |--------------------------------------------|
//   [] [trig                                 #]  |  a gate or trigger: a button
//   <> levels                                    |  an array: bars to draw
//   |   | | | | | | | |                          |
//   +--------------------------------------------+
//
// Inputs are all on the left edge, the output on the right edge, and the
// main input shares one line with the output, so a chain placed left to
// right wires straight across. A port's shape says what it carries - a
// circle audio, a diamond control, a square a gate or trigger - hollow while
// empty, filled in the source's colour when plugged, with a second ring
// when it has more than one channel. Rows come in the order
// NdefRackModule:ordered gives - main inputs, timing, the body, the output
// range and level - and each row looks the way its role says (bar, stepped,
// toggle, gate, trigger, array, signal), whatever the module.
//
// The whole module is drawn in one UserView, one widget however much it
// holds. It keeps no copy of any value: every redraw reads the module,
// every gesture writes through it to the Ndef, and whatever changed the
// Ndef - this view, your code, a loaded patch - shows on the next frame.
//
//   title, face  click to select, shift-click to add to the selection or
//                take it out; drag to move the selection
//   [x]          delete the module (cmd-z brings it back)
//   bar          drag left and right, shift for fine; click the value to
//                type one; double click resets
//   switch       click to flip; double click resets
//   array        click a cell, and drag across the cells
//   button       held down it is 1, let go it is 0
//   port         drag from the output zone to a row, or from an input to a
//                module; click once and click again to patch without
//                holding; drag a plugged input away to move or unplug it;
//                escape lets a loose cable go
//   right click  voices, copy, cut, duplicate, delete, reset, type a value ...
//   learning     a click on a control, gate or trigger arms it for a
//                controller (Mapper, or NdefRackKtl without it); the element
//                turning a control is named on it
//
// A library module that has a display - scope, spectrum - gets its
// NdefRackDisplay here, and a picture of it straight under the main row.
// A bus module gets a drop-down bar under its title, the bus it reads or
// writes to: click it for BusManager's buses, or a bus index of your own.
// A module panning among speakers - vbap, ambi - has its speaker setup
// there instead: click it for NdefRackSpeakers' table.
// =====================================================================

NdefRackModuleView {
	classvar <colW = 190, <displayW = 300, <titleH = 26, <rowH = 24, <arrayH = 48;
	classvar <displayH = 120, <busH = 24, <gapH = 6, <portR = 5;
	// more control rows than this, past the main inputs: pages, each at
	// most this many rows; the strip of their tabs
	classvar <>pageRows = 8, <tabH = 22;

	var <gui, <module, <view, <display;
	var rows, outJack, outZone, gaps, playRect, closeRect, displayRect, busRect, size;
	// compact: the button left of close, the line saying how many rows are
	// hidden, and which controls it was laid out with
	var compactRect, moreRect, <hiddenCount = 0, shown;
	// pages: their names and params, the one shown, the tabs, and the page
	// of each control not shown
	var <pages, <page = 0, tabRects, tabY, pagedOut;
	var drag, pressed, <editField, hover, flash;
	// where it sits on the rack, in the rack's own coordinates - the view
	// itself is at origin * the window's zoom
	var <origin;

	*new { |gui, module| ^super.newCopyArgs(gui, module).init }

	init {
		module.display !? { |kind|
			if (NdefRackDisplay.kinds.includes(kind)) { display = NdefRackDisplay(kind, module) }
		};
		this.prLayout;
		origin = Point(0, 0);
		// drawn and hit-tested in the rack's coordinates: the mouse's are
		// divided by the zoom, the picture scaled by it
		view = UserView(gui.canvas, Rect(0, 0, size.x * gui.zoom, size.y * gui.zoom))
			.drawFunc_({ |uv| Pen.scale(gui.zoom, gui.zoom); this.prDraw(uv) })
			.mouseDownAction_({ |uv, x, y, mod, button, clicks|
				this.prMouseDown((x @ y) / gui.zoom, mod, button, clicks)
			})
			.mouseMoveAction_({ |uv, x, y, mod| this.prMouseMove((x @ y) / gui.zoom, mod) })
			.mouseUpAction_({ |uv, x, y, mod| this.prMouseUp((x @ y) / gui.zoom, mod) });
		gui.hints.attach(view, { |v, x, y|
			var p = (x @ y) / gui.zoom;
			this.prHoverAt(p);
			gui.prArmedHint ?? { this.hintAt(p) }
		});
	}

	key { ^module.name }

	// the colour the module is marked with (NdefRack.setColor), or nil
	tagColor { ^gui.paletteColor(NdefRack.colorOf(this.key)) }

	// where it is on the rack, in the rack's coordinates whatever the zoom
	bounds { ^Rect(origin.x, origin.y, size.x, size.y) }

	size { ^size }

	moveTo { |point|
		origin = point.asPoint;
		this.prPlaceView;
	}

	// the view where the zoom puts it
	prPlaceView {
		var z = gui.zoom;
		if (view.isClosed.not) {
			view.bounds = Rect(origin.x * z, origin.y * z, size.x * z, size.y * z)
		}
	}

	refresh { if (view.isClosed.not) { view.refresh } }

	front {
		if (view.isClosed.not) { view.front };
	}

	remove {
		display !? { display.free };
		display = nil;
		editField !? { if (editField.isClosed.not) { editField.remove } };
		if (view.isClosed.not) { view.remove };
	}

	// ------------------------------------------------- geometry, for the gui

	// the centre of an input's port, in the view's own coordinates - every
	// input port is on the left edge, at x 8
	inputJack { |param|
		^rows.detect { |r| r[\name] == param } !? { |r| r[\jack] } ?? {
			// on a page not shown: the cable goes into the strip of tabs
			pagedOut !? { if (pagedOut.at(param).notNil) { Point(8, tabY) } }
		}
	}

	// the centre of the output port: the right edge of the main row
	outputJack { ^outJack }

	// the input under a point, for a cable being dropped - the whole row
	// counts, not just the port, so a drop on a control plugs into it
	inputAt { |point|
		^rows.detect { |r| r[\rect].containsPoint(point) } !? { |r| r[\name] }
	}

	// the output zone: the right half of the main row
	isOverOutput { |point| ^outZone.containsPoint(point) }

	// Where a cable dropped on the title lands: the first unplugged main
	// input (NdefRackModule:bands, the first band) of the rate the cable
	// carries - audio into audio, control into the rest - else the first main
	// input, else nil. from is the key of the module the cable comes from.
	titleInput { |from|
		var src = from !? { NdefRack.modules.at(from) };
		var ins = module.bands[0].select { |p| this.prRowFor(p[\name]).notNil };
		var fits = { |p| src.isNil or: { src.rate.isNil } or: { (p[\type] == \audio) == src.isAudio } };
		^(ins.detect { |p| module.isPatched(p[\name]).not and: { fits.(p) } } ?? { ins.first })
			!? { |p| p[\name] }
	}

	// What letting a cable from the module src go on param would do, in
	// words: "lfo → oct of lpf", then what else there is to know - the cable
	// it replaces, audio into a control, a module patched into itself, voices
	// of which only the first arrives.
	dropHint { |src, param|
		var r = this.prRowFor(param);
		var p = r !? { r[\param] };
		var srcM = src !? { NdefRack.modules.at(src) };
		var old = module.source(param);
		var oldKey = old !? { NdefRack.prModuleKey(old) };
		var notes = [];
		if (p.isNil) { ^nil };
		if (old.notNil and: { oldKey != src }) { notes = notes.add("replaces %".format(this.prSourceName(old))) };
		if (srcM.notNil and: { srcM.isAudio } and: { p[\type] != \audio }) {
			notes = notes.add("audio into a control input")
		};
		if (src == module.name) { notes = notes.add("into itself, one block late") };
		if (srcM.notNil and: { srcM.voices > 1 } and: { p[\voice] != true }
			and: { srcM.numChannels > (p[\size] ? 1) }) {
			notes = notes.add("only the first voice arrives")
		};
		^(["% → % of %".format(src ? "?", param, module.name)] ++ notes).join(" - ")
	}

	// ------------------------------------------------------------- layout

	// One column, top to bottom: the title, a bus module's drop-down, the
	// main row - the first audio main input on the left, the output on the
	// right, on one line - a display, then the rest of NdefRackModule:bands
	// in order, a hairline gap before each band after the first. The window
	// asks for the size once; a module whose controls change gets a new view.
	prLayout {
		var bands = module.bands;
		var mainIn = bands[0].detect { |p| p[\type] == \audio };
		var w = if (display.notNil) { displayW } { colW };
		var y = titleH, afterDisplay = false;
		var addRow = { |p|
			var h = if (p[\type] == \array) { arrayH } { rowH };
			rows = rows.add(this.prRow(p, y, h, w));
			y = y + h;
		};
		rows = [];
		gaps = [];
		busRect = nil;
		displayRect = nil;
		if (this.prHasDrop) {
			busRect = Rect(30, y + 3, w - 38, 18);
			y = y + busH;
		};
		outJack = Point(w - 8, y + (rowH div: 2));
		outZone = Rect(w div: 2, y, w - (w div: 2), rowH);
		mainIn !? { rows = rows.add(this.prMainRow(mainIn, y, w)) };
		y = y + rowH;
		if (display.notNil) {
			displayRect = Rect(8, y + 4, w - 16, displayH);
			y = y + 4 + displayH + 4;
			afterDisplay = true;
		};
		// compact: only what is away from its default - set or patched
		shown = this.prShownNames;
		hiddenCount = 0;
		moreRect = nil;
		if (shown.notNil) {
			bands = bands.collect { |band|
				band.select { |p|
					(p === mainIn) or: { shown.includes(p[\name]) } or: { hiddenCount = hiddenCount + 1; false }
				}
			}
		};
		bands[0].do { |p| if (p !== mainIn) { addRow.(p); afterDisplay = false } };
		pages = this.prPages(bands.drop(1));
		tabRects = nil;
		pagedOut = IdentityDictionary.new;
		if (pages.isNil) {
			// the display's own pad stands for the gap right under it
			bands.drop(1).do { |band|
				if (band.notEmpty) {
					if (afterDisplay.not) {
						gaps = gaps.add(y + (gapH div: 2));
						y = y + gapH;
					};
					afterDisplay = false;
					band.do(addRow);
				}
			};
		} {
			// pages: a strip of tabs, the rows of the page shown, as tall as
			// the tallest page so the module keeps its size from page to page
			var tw = (w - 38) / pages.size, top, tallest;
			page = (NdefRack.pageOf(this.key) ? 0).clip(0, pages.size - 1);
			// in line with the bars, so the port column stays free for cables
			tabRects = pages.collect { |pg, i| Rect(30 + (i * tw), y + 3, tw - 2, tabH - 5) };
			tabY = y + (tabH div: 2);
			y = y + tabH;
			top = y;
			pages.do { |pg, i|
				if (i != page) { pg[\bands].do { |band| band.do { |p| pagedOut.put(p[\name], i) } } }
			};
			pages[page][\bands].do { |band, i|
				if (i > 0) { gaps = gaps.add(y + (gapH div: 2)); y = y + gapH };
				band.do(addRow);
			};
			tallest = pages.collect { |pg| this.prPageHeight(pg) }.maxItem;
			y = top + tallest;
		};
		if (hiddenCount > 0) {
			moreRect = Rect(8, y + 2, w - 16, 16);
			y = y + 18;
		};
		size = Point(w, y + 6);
		playRect = Rect(6, 5, 16, 16);
		closeRect = Rect(w - 22, 5, 16, 16);
		compactRect = Rect(w - 42, 5, 16, 16);
	}

	// Pages for the controls past the main inputs, when there are more than
	// pageRows of them; nil: one page, no tabs. A module that says how
	// (NdefRackLib:pagesFor - pages: [tone: [...], rhythm: [...]]) gets its
	// own pages, in its order, and a page "more" for what it leaves out;
	// otherwise the bands - time, main, out - are kept whole and put on the
	// fewest pages, as evenly filled as they can be. A band or a page longer
	// than pageRows is cut in even pieces (main 1, main 2), never eight and the
	// rest. Each page keeps the window's order and its gaps between bands.
	prPages { |rest|
		var declared;
		if (rest.sum(_.size) <= pageRows) { ^nil };
		declared = module.kind !? { NdefRackLib.pagesFor(module.kind) };
		^(if (declared.notNil) { this.prDeclaredPages(rest, declared) } { this.prEvenPages(rest) })
			!? { |out| if (out.size > 1) { out } }
	}

	// the module's own pages: the controls it names that are here (compact,
	// voices), cut when a page is too long; empty pages left out
	prDeclaredPages { |rest, declared|
		var all = rest.flat, placed = IdentitySet.new, wanted = declared.copy, out = [];
		var group = { |ps| rest.collect { |band| band.select { |p| ps.includes(p) } }.reject(_.isEmpty) };
		declared.do { |d| d[\names].do { |n| placed.add(n) } };
		wanted = wanted.add((name: "more", names: all.reject { |p| placed.includes(p[\name]) }.collect { |p| p[\name] }));
		wanted.do { |d|
			var ps = all.select { |p| d[\names].includes(p[\name]) };
			var cut = this.prSplit(ps);
			cut.do { |c, ci|
				if (c.notEmpty) {
					out = out.add((name: d[\name] ++ if (cut.size > 1) { " " ++ (ci + 1) } { "" },
						bands: group.(c), count: c.size))
				}
			}
		};
		^out
	}

	// the bands whole (cut evenly when longer than a page), then grouped on
	// the fewest pages, the most even of those: the best way to page the
	// first n pieces, for n one at a time, from the best ways before it
	prEvenPages { |rest|
		var names = #["time", "main", "out"];
		var pieces = [], ways, best;
		rest.do { |band, bi|
			var cut = this.prSplit(band);
			cut.do { |c, ci|
				if (c.notEmpty) {
					pieces = pieces.add((name: names[bi] ++ if (cut.size > 1) { " " ++ (ci + 1) } { "" }, ps: c))
				}
			}
		};
		if (pieces.isEmpty) { ^nil };
		// ways[n]: [cost, where the last page starts] for the first n pieces
		ways = [[0, 0]];
		pieces.size.do { |i|
			var found;
			(i + 1).do { |start|
				var rows = pieces.copyRange(start, i).sum { |pc| pc[\ps].size };
				if (rows <= pageRows) {
					var cost = ways[start][0] + 10000 + rows.squared;
					if (found.isNil or: { cost < found[0] }) { found = [cost, start] }
				}
			};
			ways = ways.add(found);
		};
		best = List.new;
		{
			var end = pieces.size;
			while { end > 0 } {
				var start = ways[end][1];
				best.addFirst(pieces.copyRange(start, end - 1));
				end = start;
			}
		}.value;
		^best.asArray.collect { |g|
			(name: g.collect { |pc| pc[\name] }.join(" · "), bands: g.collect { |pc| pc[\ps] },
				count: g.sum { |pc| pc[\ps].size })
		}
	}

	// a list in as few even pieces of at most pageRows as it takes: 9 is 5 and 4
	prSplit { |list|
		var k = (list.size / pageRows).roundUp.asInteger.max(1);
		var per = list.size / k;
		^k.collect { |i| list.copyRange((i * per).round.asInteger, ((i + 1) * per).round.asInteger - 1) }
	}

	prPageHeight { |pg|
		^pg[\bands].inject(0, { |h, band, i|
			h + (if (i > 0) { gapH } { 0 }) + band.sum { |p| if (p[\type] == \array) { arrayH } { rowH } }
		})
	}

	// the page a control is on, or nil when every control shows
	pageFor { |name|
		^pages !? { pages.detectIndex { |pg| pg[\bands].any { |b| b.any { |p| p[\name] == name } } } }
	}

	// Compact: the names of the controls that are away from their defaults -
	// patched, or set to anything but the default. Triggers only when
	// patched. nil when the module shows everything.
	prShownNames {
		if (NdefRack.isCompact(this.key).not) { ^nil };
		^module.params.select { |p|
			var name = p[\name];
			module.isPatched(name) or: {
				p[\type] != \trigger and: { module.value(name) != p[\default] }
			}
		}.collect { |p| p[\name] }
	}

	isCompact { ^shown.notNil }

	// laid out compact for other controls than the ones away from their
	// defaults now: the window makes it again
	isStale { ^shown.notNil and: { this.prShownNames != shown } and: { drag.isNil } }

	// the main input: the left half of the main row, all of it the port
	prMainRow { |p, y, w|
		var half = w div: 2;
		^(param: p, name: p[\name], type: p[\type], main: true,
			rect: Rect(0, y, half, rowH), port: Rect(0, y, half, rowH),
			jack: Point(8, y + (rowH div: 2)), label: Rect(20, y + 3, half - 22, 18))
	}

	// A row: the port at the left edge, its hit square 24 px, a dead strip to
	// x 30, then the control in a bar to 8 px from the right edge. The whole
	// row is where a cable drops.
	prRow { |p, y, h, w|
		var r = (param: p, name: p[\name], type: p[\type], role: p[\role], rect: Rect(0, y, w, h),
			port: Rect(0, y, 24, rowH), jack: Point(8, y + (rowH div: 2)));
		var bar = Rect(30, y + 3, w - 38, 18);
		switch(p[\role],
			\array, {
				// the label line above the bars; the port's hit stops where they start
				r[\port] = Rect(0, y, 24, 18);
				r[\label] = Rect(30, y + 2, w - 38, 15);
				r[\bars] = Rect(8, y + 18, w - 16, h - 20);
			},
			\signal, { r[\label] = bar },
			{
				// \bar, \stepped, \toggle - and \trigger, \gate: the bar is the button
				r[\bar] = bar;
			}
		);
		^r
	}

	prRowAt { |point| ^rows.detect { |r| r[\rect].containsPoint(point) } }

	// The mouse over the view, from the hint bar's mouse-over: an array shows
	// the index and value under it. prHoverOut, from the canvas's, clears it.
	prHoverAt { |point|
		var r = this.prRowAt(point);
		var now = if (r.notNil and: { r[\role] == \array } and: { r[\bars].containsPoint(point) }) {
			[r[\name], ((point.x - r[\bars].left) / r[\bars].width * (r[\param][\size] ? 1))
				.floor.clip(0, (r[\param][\size] ? 1) - 1).asInteger]
		};
		if (now != hover) { hover = now; this.refresh };
	}

	prHoverOut { if (hover.notNil) { hover = nil; this.refresh } }

	// ------------------------------------------------------------- drawing

	prDraw { |uv|
		var c = NdefRackGUI;
		var face = Rect(0.5, 0.5, size.x - 1, size.y - 1);
		var selected = gui.isSelected(this.key);
		var tag = this.tagColor;
		Pen.fillColor = c.colPanel;
		Pen.addRoundedRect(face, 5, 5);
		Pen.fill;
		// a colour of its own: the edge in it - white still when selected
		Pen.strokeColor = if (selected) { c.colAccent } { tag ?? { c.colEdge } };
		Pen.width = if (selected or: { tag.notNil }) { 2 } { 1 };
		Pen.addRoundedRect(if (selected) { face.insetBy(0.5, 0.5) } { face }, 5, 5);
		Pen.stroke;
		this.prDrawTitle;
		busRect !? { this.prDrawBus };
		Pen.width = 1;
		Pen.strokeColor = c.colEdge;
		gaps.do { |y| Pen.line(Point(8, y + 0.5), Point(size.x - 8, y + 0.5)) };
		Pen.stroke;
		rows.do { |r| this.prDrawRow(r) };
		displayRect !? { this.prDrawDisplay };
		this.prDrawOutput;
	}

	// play at the left (a diamond badge on a control rate module), the name,
	// then right-aligned before the close button: voices, the recording dot,
	// the library module it came from
	prDrawTitle {
		var c = NdefRackGUI;
		var stripe = this.prRateColor;
		var x = compactRect.left - 4;
		var rec = NdefRack.recordSource(module.name);
		var str, w;
		// the title tinted by the module's own colour, a quarter of the way;
		// the stripe under it stays what the module puts out
		Pen.fillColor = this.tagColor !? { |t| c.colTitle.blend(t, 0.28) } ?? { c.colTitle };
		Pen.addRoundedRect(Rect(1, 1, size.x - 2, titleH - 1), 4, 4);
		Pen.fill;
		Pen.fillColor = stripe;
		Pen.fillRect(Rect(1, titleH - 2, size.x - 2, 2));
		// a library module, where it came from
		module.kind !? { |k|
			if (k != module.name) {
				str = k.asString;
				w = str.bounds(gui.font).width.min(70);
				x = x - w;
				Pen.stringInRect(str, Rect(x, 6, w + 1, 14), gui.font, c.colDim, QAlignment(\right));
				x = x - 8;
			}
		};
		// recorded by BusRecorder: a red dot, a ring when disarmed
		rec !? {
			this.prDrawRec(rec, Point(x - 4, playRect.center.y));
			x = x - 14;
		};
		if (module.voices > 1) {
			str = "×%".format(module.voices);
			w = str.bounds(gui.boldFont).width;
			x = x - w;
			Pen.stringAtPoint(str, x @ 5, gui.boldFont, c.colText);
			x = x - 8;
		};
		Pen.stringAtPoint(this.prElide(module.name.asString, gui.boldFont, x - 28), 28 @ 5,
			gui.boldFont, c.colText);
		case
		{ module.isAudio } { this.prDrawPlay }
		{ module.isControl } { this.prDrawShape(playRect.center, \diamond, 5, c.colControl) };
		this.prDrawClose;
		this.prDrawCompact;
		tabRects !? { this.prDrawTabs };
	}

	// str cut to fit width, with an ellipsis
	prElide { |str, font, width|
		var s = str;
		if (str.bounds(font).width <= width) { ^str };
		while { s.size > 1 and: { (s ++ "…").bounds(font).width > width } } { s = s.drop(-1) };
		^s ++ "…"
	}

	prDrawRec { |src, mid|
		var r = Rect.aboutPoint(mid, 4, 4);
		var col = NdefRackGUI.colRec;
		var taking = NdefRack.recorder.isRecording and: { src.armed };
		if (src.armed) {
			Pen.fillColor = if (taking) { col } { col.copy.alpha_(0.75) };
			Pen.fillOval(r);
		} {
			Pen.width = 1.2;
			Pen.strokeColor = col;
			Pen.strokeOval(r.insetBy(0.6, 0.6));
		};
	}

	prDrawPlay {
		var c = NdefRackGUI;
		var on = module.isMonitoring;
		var mid = playRect.center;
		Pen.fillColor = if (on) { c.colAccent } { c.colKnob };
		Pen.addRoundedRect(playRect, 3, 3);
		Pen.fill;
		Pen.fillColor = if (on) { Color.black } { c.colText };
		if (on) {
			Pen.fillRect(Rect.aboutPoint(mid, 3.5, 3.5));
		} {
			Pen.moveTo(mid + Point(-3, -4.5));
			Pen.lineTo(mid + Point(4.5, 0));
			Pen.lineTo(mid + Point(-3, 4.5));
			Pen.lineTo(mid + Point(-3, -4.5));
			Pen.fill;
		};
	}

	// Compact, left of close: a box with three lines - every row shows -
	// or one line and a filled box - compact. Under the rows of a compact
	// module, how many are at their defaults.
	prDrawCompact {
		var r = compactRect.insetBy(2.5, 3.5);
		var on = this.isCompact;
		Pen.width = 1;
		Pen.strokeColor = if (on) { NdefRackGUI.colText } { Color.grey(0.44) };
		Pen.addRoundedRect(r, 2, 2);
		Pen.stroke;
		if (on) {
			Pen.fillColor = NdefRackGUI.colText;
			Pen.fillRect(Rect(r.left + 2, r.center.y - 1, r.width - 4, 2));
		} {
			Pen.strokeColor = Color.grey(0.44);
			[0.3, 0.55, 0.8].do { |f|
				var y = r.top + (r.height * f);
				Pen.line(Point(r.left + 2, y), Point(r.right - 2, y));
			};
			Pen.stroke;
		};
		moreRect !? {
			Pen.stringInRect("+ % at default".format(hiddenCount), moreRect, gui.font,
				NdefRackGUI.colDim, QAlignment(\center));
		};
	}

	// The tabs: the page shown filled, the others dim; a dot on a tab with a
	// patched control on it, in its cable's colour; and, when one of those
	// is on a page not shown, a port on the left edge where its cable ends.
	prDrawTabs {
		var c = NdefRackGUI;
		var hiddenPatched = false;
		tabRects.do { |r, i|
			var on = i == page;
			var patched = pages[i][\bands].flat.select { |p| module.isPatched(p[\name]) };
			Pen.fillColor = if (on) { c.colKnob } { c.colTrack };
			Pen.addRoundedRect(r, 3, 3);
			Pen.fill;
			Pen.stringInRect(this.prElide(pages[i][\name], gui.font, r.width - 10), r.insetBy(4, 0), gui.font,
				if (on) { c.colText } { c.colDim }, QAlignment(\center));
			if (patched.notEmpty) {
				Pen.fillColor = gui.cableColor(this.key, patched[0][\name]);
				Pen.fillOval(Rect.aboutPoint(Point(r.right - 5, r.top + 4), 2.5, 2.5));
				if (on.not) { hiddenPatched = true };
			};
		};
		if (hiddenPatched) {
			Pen.strokeColor = c.colJackRing;
			Pen.width = 1.5;
			Pen.strokeOval(Rect.aboutPoint(Point(8, tabY), portR - 1, portR - 1));
		};
	}

	// dim: the far end from play
	prDrawClose {
		var mid = closeRect.center;
		Pen.width = 1.5;
		Pen.strokeColor = Color.grey(0.44);
		Pen.line(mid + Point(-3.5, -3.5), mid + Point(3.5, 3.5));
		Pen.line(mid + Point(-3.5, 3.5), mid + Point(3.5, -3.5));
		Pen.stroke;
	}

	// the bus it reads or writes to, as a drop-down bar: bright while
	// BusManager knows no bus of that name, dim before it has one
	// A module with speakers has its setup there, bright while
	// NdefRackSpeakers has no setup of that name.
	prDrawBus {
		var c = NdefRackGUI;
		var bus = module.bus;
		var speakers = module.canSpeakers;
		var mid = Point(busRect.right - 11, busRect.center.y);
		Pen.fillColor = c.colKnob;
		Pen.addRoundedRect(busRect, 3, 3);
		Pen.fill;
		Pen.stringInRect(if (speakers) { this.speakersLabel } { this.busLabel },
			Rect(busRect.left + 7, busRect.top, busRect.width - 26, busRect.height),
			gui.font, case
				{ speakers } { if (module.speakersKnown) { c.colText } { c.colAccent } }
				{ bus.isNil } { c.colDim }
				{ bus.isNumber or: { this.prBusKnown } } { c.colText }
				{ c.colAccent }, QAlignment(\left));
		Pen.fillColor = c.colDim;
		Pen.moveTo(mid + Point(-4, -2));
		Pen.lineTo(mid + Point(4, -2));
		Pen.lineTo(mid + Point(0, 3));
		Pen.lineTo(mid + Point(-4, -2));
		Pen.fill;
	}

	prDrawRow { |r|
		var name = r[\name];
		var src = module.source(name);
		var color = src !? { gui.cableColor(this.key, name) };
		var target = gui.dragTarget == [this.key, name];
		if (target) { this.prDrawTarget(r[\rect]) };
		this.prDrawPort(r, color, if (target) { gui.dragColor });
		if (r[\main] == true) { ^this.prDrawMainInput(r, src, color) };
		switch(r[\role],
			\signal, { this.prDrawAudioRow(r, src, color) },
			\array, { this.prDrawArrayRow(r, src, color) },
			\toggle, { this.prDrawToggleRow(r, src, color) },
			\trigger, { this.prDrawButtonRow(r, src, color) },
			\gate, { this.prDrawButtonRow(r, src, color) },
			{ this.prDrawBarRow(r, src, color) }
		);
		this.prDrawKtl(r);
	}

	// Learning, every control a controller can turn is outlined, the armed
	// one filled. The element turning a control is named in its bar, just
	// left of the value: dim when bound, in blue while follow turns it.
	prDrawKtl { |r|
		var c = NdefRackGUI;
		var ktl = NdefRackMapper.controlOf(this.key, r[\name]);
		if (NdefRackMapper.learning and: { this.prLearnable(r) }) {
			if (NdefRackMapper.armed == [this.key, r[\name]]) {
				Pen.fillColor = c.colAccent.copy.alpha_(0.22);
				Pen.addRoundedRect(r[\rect].insetBy(1, 1), 3, 3);
				Pen.fill;
			};
			Pen.width = 1;
			Pen.strokeColor = c.colAccent.copy.alpha_(if (ktl.isNil) { 0.35 } { 0.8 });
			Pen.addRoundedRect(r[\rect].insetBy(1.5, 1.5), 3, 3);
			Pen.stroke;
		};
		ktl !? {
			var str = ktl[\element].asString;
			var w = str.bounds(gui.font).width + 2;
			var right = this.prTagRight(r);
			Pen.stringInRect(str, Rect(right - w, r[\rect].top + 4, w, 16), gui.font,
				if (ktl[\follow]) { c.colControl } { this.class.colQuiet }, QAlignment(\right));
		};
	}

	// where a controller's tag ends: before the value, the switch, the lamp
	prTagRight { |r|
		var bar = r[\bar];
		^switch(r[\role],
			\toggle, { this.prSwitchRect(r).left - 6 },
			\trigger, { bar.right - 20 },
			\gate, { bar.right - 20 },
			\array, { r[\label].right - 60 },
			\signal, { r[\label].right },
			{ this.prValueRect(r).left - 6 }
		)
	}

	// Where a loose cable would land: outlined, filled faintly in the colour
	// of what it carries.
	prDrawTarget { |rect|
		var c = NdefRackGUI;
		Pen.fillColor = (gui.dragColor ? c.colAccent).copy.alpha_(0.16);
		Pen.addRoundedRect(rect.insetBy(1, 1), 3, 3);
		Pen.fill;
		Pen.width = 2;
		Pen.strokeColor = c.colAccent;
		Pen.addRoundedRect(rect.insetBy(1, 1), 3, 3);
		Pen.stroke;
	}

	// a port's radius: grown while a loose cable could land on it - the
	// inputs for a cable from an output, the output for one from an input
	prPortRadius { |input = true|
		var mode = gui.cableMode;
		^if (input) {
			if (mode == \out or: { mode == \carry }) { 7 } { portR }
		} {
			if (mode == \in) { 7 } { portR }
		}
	}

	// what a controller element can turn: a \knob (a bar, stepped or toggle row), a gate, a trigger
	prLearnable { |r| ^#[\knob, \gate, \trigger].includes(r[\type]) }

	// text colours: a parameter's name, a value at its default, a changed one
	*colName { ^Color.grey(0.878) }
	*colQuiet { ^Color.grey(0.66) }
	*colChanged { ^Color.white }

	// whether a control holds its default - the value text is dim then
	prAtDefault { |r|
		var d = r[\param][\default].asArray;
		^module.value(r[\name]).asArray.every { |v, i| (v - d.wrapAt(i)).abs < 1e-6 }
	}

	// the value as the bar writes it, with its unit
	prReadout { |r, value|
		var spec = r[\param][\spec];
		var str, units;
		// a selector the library names: the name of the nearest stop
		this.prLabelAt(r, value) !? { |name| ^name };
		str = NdefRackSpec.readout(r[\name], spec, value.asArray.first ? 0);
		units = spec.tryPerform(\units) ? "";
		if (NdefRackSpec.unitFor(r[\name], spec).isNil and: { units.notEmpty }
			and: { units != "guess" }) { str = str + units };
		// a row whose name is its unit (oct, bpm) does not say it twice
		^if (str.endsWith(" " ++ r[\name].asString)) {
			str.keep(str.size - r[\name].asString.size - 1)
		} { str }
	}

	// The name of the stop nearest to value, on a control whose stops the
	// library names (labels:) - the first name at the control's lowest value,
	// the next one higher - or nil.
	prLabelAt { |r, value|
		var names = r[\param][\labels];
		var spec = r[\param][\spec];
		var v = value.asArray.first;
		if (names.isNil or: { v.isNumber.not }) { ^nil };
		^names.clipAt((v - spec.clipLo).round.asInteger.clip(0, names.size - 1))
	}

	// what the right end of a bar says: its value - on a patched control what
	// the cable brings in now, or nothing for an audio cable
	prValueString { |r|
		var src = module.source(r[\name]);
		^if (src.notNil) {
			this.prLive(r, src) !? { |live| this.prReadout(r, live) } ? ""
		} {
			this.prReadout(r, module.value(r[\name]))
		}
	}

	// the value text's rect in a bar: a click there types a value
	prValueRect { |r|
		var bar = r[\bar];
		var w = this.prValueString(r).bounds(gui.font).width.max(12) + 2;
		^Rect(bar.right - 6 - w, bar.top, w, bar.height)
	}

	// the row of a param, for the checks and the menus
	prRowFor { |name| ^rows.detect { |r| r[\name] == name } }

	// a name at the left of a bar - after the voices' glyph on a control with
	// a value per voice - and where it comes from when it is patched. ink,
	// when given, for every word (a lit button's dark ink).
	prDrawName { |r, rect, src, color, ink|
		var c = NdefRackGUI;
		var x = rect.left + 6;
		var str = r[\name].asString;
		var w;
		if (r[\param][\voice] == true) {
			Pen.width = 1;
			Pen.strokeColor = ink ? c.colDim;
			[-3, 0, 3].do { |dy|
				Pen.line(Point(rect.left + 4, rect.center.y + dy + 0.5), Point(rect.left + 10, rect.center.y + dy + 0.5))
			};
			Pen.stroke;
			x = x + 10;
		};
		str = this.prElide(str, gui.font, rect.width * 0.55);
		w = str.bounds(gui.font).width;
		Pen.stringInRect(str, Rect(x, rect.top, w + 2, rect.height), gui.font, ink ? this.class.colName,
			QAlignment(\left));
		^src !? {
			var from = this.prElide("← " ++ this.prSourceName(src), gui.font, rect.width * 0.4);
			Pen.stringInRect(from, Rect(x + w + 5, rect.top, rect.width, rect.height), gui.font,
				ink ? this.class.colQuiet, QAlignment(\left));
			x + w + 5 + from.bounds(gui.font).width
		} ? (x + w)
	}

	// A bar: a fill from the left - from a zero line on a range across 0 -
	// to the value, a notch at the default, a tick per step on a control of
	// 16 steps or fewer, a thin line per voice when the voices differ. The
	// name at the left, the value at the right, dim while it is the default.
	// Patched: no fill, a line where the cable is now.
	prDrawBarRow { |r, src, color|
		var c = NdefRackGUI;
		var spec = r[\param][\spec];
		var bar = r[\bar];
		var values = module.value(r[\name]).asArray;
		var xOf = { |v| bar.left + (spec.unmap(v) * bar.width) };
		var zero = if (spec.clipLo < 0 and: { spec.clipHi > 0 }) { xOf.(0) } { bar.left };
		var vr = this.prValueRect(r);
		Pen.fillColor = c.colTrack;
		Pen.addRoundedRect(bar, 3, 3);
		Pen.fill;
		if (src.isNil) {
			var x = xOf.(values.first ? 0);
			// the lines stop short of the words: only their ends show under
			// the name and the value
			var words = [
				[bar.left + 3, bar.left + 11 + this.prNameWidth(r, bar, nil)],
				[vr.left - 5, vr.right + 2]
			];
			Pen.fillColor = c.colValue.copy.alpha_(0.32);
			Pen.fillRect(Rect(min(zero, x), bar.top + 1, (x - zero).abs, bar.height - 2));
			Pen.fillColor = c.colValue;
			this.prMark(x, 2, bar, words);
			// voices apart: a thin line for each
			if (values.size > 1 and: { values.any { |v| (v - values.first).abs > 1e-6 } }) {
				Pen.fillColor = c.colValue.copy.alpha_(0.55);
				values.do { |v| this.prMark(xOf.(v), 1, bar.insetBy(0, 2), words) };
			};
		} {
			// patched: where the cable has been lately - a band per voice - and
			// a line where it is now
			this.prBands(r, src).do { |lohi|
				var x0 = xOf.(lohi[0]), x1 = xOf.(lohi[1]);
				Pen.fillColor = color.copy.alpha_(0.25);
				Pen.fillRect(Rect(x0, bar.top + 1, (x1 - x0).max(1), bar.height - 2));
			};
			this.prLive(r, src) !? { |live|
				Pen.fillColor = color;
				live.asArray.do { |v| Pen.fillRect(Rect(xOf.(v) - 1, bar.top + 1, 2, bar.height - 2)) };
			};
		};
		// the zero line of a range across 0
		if (zero > bar.left) {
			Pen.fillColor = c.colEdge;
			Pen.fillRect(Rect(zero - 0.5, bar.top + 1, 1, bar.height - 2));
		};
		// a tick per step - and on a selector whose stops have names, at
		// every whole number
		case
		{ r[\role] == \stepped } { this.prDrawTicks(bar, spec) }
		{ r[\param][\labels].notNil } { this.prDrawTicks(bar, spec, 1) };
		// the default's notch
		Pen.fillColor = Color.grey(0.62);
		Pen.fillRect(Rect(xOf.(r[\param][\default].asArray.first ? 0) - 0.5, bar.top, 1, 4));
		Pen.fillRect(Rect(xOf.(r[\param][\default].asArray.first ? 0) - 0.5, bar.bottom - 4, 1, 4));
		if (src.notNil) {
			this.prDrawPatched(bar, color);
			// its own value, a triangle under the bar: what it goes back to
			// when the cable is unplugged
			this.prDrawOwn(Point(xOf.(values.first ? 0), bar.bottom));
			// the live lines and the band pass under the words, not through them
			this.prBacking(Rect(bar.left + 2, bar.top + 2, this.prNameWidth(r, bar, src) + 6, bar.height - 4));
			this.prBacking(vr.insetBy(-2, 2));
		};
		this.prDrawName(r, bar, src, color);
		Pen.stringInRect(this.prValueString(r), vr, gui.font,
			if (src.isNil and: { this.prAtDefault(r) }) { this.class.colQuiet } { this.class.colChanged },
			QAlignment(\right));
	}

	// a value line across the bar, or only its two ends where it would cross
	// words (words: [left, right] pairs)
	prMark { |x, w, bar, words|
		if (words.any { |lr| x >= lr[0] and: { x <= lr[1] } }) {
			Pen.fillRect(Rect(x - (w / 2), bar.top + 1, w, 3));
			Pen.fillRect(Rect(x - (w / 2), bar.bottom - 4, w, 3));
		} {
			Pen.fillRect(Rect(x - (w / 2), bar.top + 1, w, bar.height - 2));
		}
	}

	// A patched control: no fill, a dashed outline in the colour of what
	// the cable carries.
	prDrawPatched { |bar, color|
		Pen.use {
			Pen.width = 1;
			Pen.lineDash = FloatArray[3, 3];
			Pen.strokeColor = color ? NdefRackGUI.colDim;
			Pen.addRoundedRect(bar.insetBy(0.5, 0.5), 3, 3);
			Pen.stroke;
		};
	}

	// a hollow triangle under the bar, its point on the bar's bottom edge -
	// in the gap below it, clear of the words
	prDrawOwn { |at|
		Pen.width = 1;
		Pen.strokeColor = NdefRackGUI.colText;
		Pen.moveTo(at + Point(0, 0));
		Pen.lineTo(at + Point(2.5, 3.5));
		Pen.lineTo(at + Point(-2.5, 3.5));
		Pen.lineTo(at + Point(0, 0));
		Pen.stroke;
	}

	// the track's colour behind a few words, so lines under them stay behind
	prBacking { |rect|
		Pen.fillColor = NdefRackGUI.colTrack;
		Pen.addRoundedRect(rect, 2, 2);
		Pen.fill;
	}

	// how wide prDrawName's words are, glyph and source included
	prNameWidth { |r, rect, src|
		var str = this.prElide(r[\name].asString, gui.font, rect.width * 0.55);
		var w = str.bounds(gui.font).width + if (r[\param][\voice] == true) { 10 } { 0 };
		src !? {
			w = w + 5 + this.prElide("← " ++ this.prSourceName(src), gui.font, rect.width * 0.4).bounds(gui.font).width
		};
		^w
	}

	// The band a cable into this control has covered over the last
	// historyLength frames, [lo, hi] - one per voice on a control with a value
	// per voice fed a channel each. None for an audio cable: no live reading.
	prBands { |r, src|
		var n = if (r[\param][\voice] == true and: { src.numChannels > 1 }) {
			src.numChannels.min(module.voices)
		} { 1 };
		^n.collect { |k| gui.liveRange(src, k) }.reject(_.isNil)
	}

	prDrawTicks { |bar, spec, step|
		var n;
		step = step ? spec.step;
		if (step <= 0) { ^this };
		n = ((spec.clipHi - spec.clipLo) / step).round.asInteger;
		// (1..0) would count down: one step has no tick inside it
		if (n < 2 or: { n > 16 }) { ^this };
		Pen.fillColor = Color.grey(0.45);
		(1..n - 1).do { |i|
			var x = bar.left + (spec.unmap(spec.clipLo + (i * step)) * bar.width);
			Pen.fillRect(Rect(x - 0.5, bar.bottom - 5, 1, 4));
		};
	}

	// A switch: the name at the left, a 28 x 14 switch at the right reading
	// on or off - on while the value is above 0.5, patched or not.
	prDrawToggleRow { |r, src, color|
		var c = NdefRackGUI;
		var bar = r[\bar];
		var v = src !? { this.prLive(r, src) !? { |l| l.asArray.first } } ?? { module.value(r[\name]).asArray.first ? 0 };
		var on = v > 0.5;
		var sw = this.prSwitchRect(r);
		Pen.fillColor = c.colTrack;
		Pen.addRoundedRect(bar, 3, 3);
		Pen.fill;
		this.prDrawName(r, bar, src, color);
		Pen.fillColor = case { on.not } { c.colKnob } { src.notNil } { color } { c.colValue };
		Pen.addRoundedRect(sw, 3, 3);
		Pen.fill;
		if (src.notNil) { this.prDrawPatched(bar, color) };
		Pen.stringCenteredIn(this.prSwitchText(r, on), sw, gui.font,
			if (on) { Color.black } { if (this.prAtDefault(r)) { this.class.colQuiet } { this.class.colChanged } });
	}

	// what a switch reads: on or off, or the library's names for its two
	// stops (labels:) - dfm1's lowpass, highpass
	prSwitchText { |r, on|
		var names = r[\param][\labels];
		^if (names.notNil and: { names.size >= 2 }) { names[if (on) { 1 } { 0 }] } {
			if (on) { "on" } { "off" }
		}
	}

	// the switch at the right of a toggle: 28 x 14, or as wide as the longer
	// of its two names
	prSwitchRect { |r|
		var bar = r[\bar];
		var names = r[\param][\labels];
		var w = 28;
		if (names.notNil and: { names.size >= 2 }) {
			w = max(w, names.keep(2).collect { |s| s.bounds(gui.font).width }.maxItem + 10)
				.min(bar.width * 0.5).ceil;
		};
		^Rect(bar.right - 4 - w, bar.center.y - 7, w, 14)
	}

	// A gate or a trigger: a button the bar wide with its own name, a lamp
	// (gate) or an arrow (trigger) at the right. It fills while it is high -
	// held down, flashing after it fired, or a cable bringing above 0.5.
	prDrawButtonRow { |r, src, color|
		var c = NdefRackGUI;
		var bar = r[\bar];
		var live = src !? { this.prLive(r, src) !? { |l| l.asArray.first } };
		var held = pressed == r[\name] or: {
			flash.notNil and: { (flash[r[\name]] ? 0) > Main.elapsedTime }
		};
		var lit = held or: {
			(live ?? { if (src.isNil) { module.value(r[\name]).asArray.first } }) ? 0 > 0.5
		};
		var ink = if (lit) { Color.black } { this.class.colName };
		var mid = Point(bar.right - 10, bar.center.y);
		Pen.fillColor = case
			{ lit } { color ? c.colValue }
			{ src.notNil } { c.colTrack }
			{ c.colKnob };
		Pen.addRoundedRect(bar, 3, 3);
		Pen.fill;
		// patched or not, lit or not, the same words and outline: only the
		// fill and the lamp follow the value (dark ink on the lit fill)
		this.prDrawName(r, bar, src, color, if (lit) { ink });
		if (src.notNil) { this.prDrawPatched(bar.insetBy(1, 1), if (lit) { Color.black } { color }) };
		if (r[\role] == \trigger) {
			Pen.fillColor = if (lit) { Color.black } { c.colDim };
			Pen.moveTo(mid + Point(-2.5, -4));
			Pen.lineTo(mid + Point(3.5, 0));
			Pen.lineTo(mid + Point(-2.5, 4));
			Pen.lineTo(mid + Point(-2.5, -4));
			Pen.fill;
		} {
			if (lit) {
				Pen.fillColor = Color.black;
				Pen.fillRect(Rect.aboutPoint(mid, 3, 3));
			} {
				Pen.width = 1;
				Pen.strokeColor = c.colDim;
				Pen.strokeRect(Rect.aboutPoint(mid, 2.5, 2.5));
			};
		};
	}

	// An array: a label line - the name, and the index and value under the
	// mouse - over the bars, which grow from the zero line on a range across 0.
	// A control stepping by 1 has a toggle cell per value instead.
	prDrawArrayRow { |r, src, color|
		var c = NdefRackGUI;
		var spec = r[\param][\spec];
		var values = module.value(r[\name]).asArray;
		var live = src !? { gui.liveValues(src) };
		var bars = r[\bars];
		var n = values.size.max(1);
		var bw = bars.width / n;
		var cells = spec.step == 1;
		var zero = spec.unmap(0.clip(spec.clipLo, spec.clipHi));
		var at = drag !? { if (drag[\row] === r) { drag[\last] } }
			?? { if (hover.notNil and: { hover[0] == r[\name] }) { hover[1] } };
		this.prDrawName(r, r[\label].copy.left_(r[\label].left - 6), src, color);
		at !? {
			if (at < values.size) {
				Pen.stringInRect("%: %".format(at + 1, this.prReadout(r, values[at])), r[\label], gui.font,
					this.class.colQuiet, QAlignment(\right));
			}
		};
		Pen.fillColor = c.colTrack;
		Pen.fillRect(bars);
		values.do { |v, i|
			// a narrower cable fills the first slots; the rest keep their values
			var shown = if (live.notNil and: { i < live.size }) { live[i] } { v };
			var norm = spec.unmap(shown);
			var x = bars.left + (i * bw) + 1;
			var fill = if (src.notNil and: { live.notNil } and: { i < live.size }) { color } { c.colValue };
			var y0, y1;
			if (cells) {
				Pen.fillColor = if (shown > 0.5) { fill } { c.colKnob };
				Pen.addRoundedRect(Rect(x, bars.top + 1, (bw - 2).max(1), bars.height - 2), 2, 2);
				Pen.fill;
			} {
				y0 = bars.bottom - (zero * bars.height);
				y1 = bars.bottom - (norm * bars.height);
				Pen.fillColor = fill.copy.alpha_(if (at == i) { 1 } { 0.85 });
				Pen.fillRect(Rect(x, min(y0, y1), (bw - 2).max(1), (y1 - y0).abs.max(1)));
			};
		};
		if (cells.not and: { zero > 0 }) {
			Pen.fillColor = c.colEdge;
			Pen.fillRect(Rect(bars.left, bars.bottom - (zero * bars.height) - 0.5, bars.width, 1));
		};
	}

	prDrawAudioRow { |r, src, color|
		var c = NdefRackGUI;
		var name = r[\name].asString;
		var w = name.bounds(gui.font).width;
		Pen.stringInRect(name, r[\label], gui.font, this.class.colName, QAlignment(\left));
		src !? {
			Pen.stringInRect("← " ++ this.prSourceName(src),
				Rect(r[\label].left + w + 6, r[\label].top, r[\label].width - w - 6, r[\label].height),
				gui.font, this.class.colQuiet, QAlignment(\left));
		};
	}

	// the main input, on the left half of the main row: its name in bold,
	// where it comes from dim
	prDrawMainInput { |r, src, color|
		var c = NdefRackGUI;
		var name = r[\name].asString;
		var w = name.bounds(gui.boldFont).width;
		var lab = r[\label];
		Pen.stringInRect(name, lab, gui.boldFont, c.colText, QAlignment(\left));
		src !? {
			Pen.stringInRect(this.prElide("← " ++ this.prSourceName(src), gui.font, lab.width - w - 6),
				Rect(lab.left + w + 6, lab.top, lab.width - w - 6, lab.height),
				gui.font, this.class.colQuiet, QAlignment(\left));
		};
	}

	// What a cable brings into a control, now: a value per voice from a source
	// with as many channels as there are voices, else its first channel.
	prLive { |r, src|
		^if (r[\param][\voice] == true and: { src.numChannels > 1 }) {
			gui.liveValues(src) !? { |vals| vals.keep(module.voices) }
		} {
			gui.liveValue(src, 0)
		}
	}

	// An input's port: its shape says what it expects - a circle for audio,
	// a square for a gate or trigger, a diamond for the rest - hollow until
	// plugged, then filled in the cable's colour with a stub to the edge,
	// where the cable meets the view. A second ring: more than one channel.
	prDrawPort { |r, color, target|
		var c = NdefRackGUI;
		var p = r[\param];
		var rad = this.prPortRadius(true);
		var shape = case
			{ p[\type] == \audio } { \circle }
			{ #[\gate, \trigger].includes(p[\type]) } { \square }
			{ \diamond };
		var mid = r[\jack];
		color !? {
			Pen.fillColor = color;
			Pen.fillRect(Rect(0, mid.y - 1, mid.x - portR + 1, 2));
		};
		this.prDrawShape(mid, shape, rad, target ? color, (p[\size] ? 1) > 1, rad > portR);
	}

	// a port's shape, about mid: filled with fill, else hollow
	prDrawShape { |mid, shape, radius, fill, wide = false, ring = false|
		var c = NdefRackGUI;
		var path = { |rad|
			switch(shape,
				\circle, { Pen.addOval(Rect.aboutPoint(mid, rad, rad)) },
				\square, { Pen.addRect(Rect.aboutPoint(mid, rad, rad)) },
				{
					// a diamond, 12 px across at radius 5
					var d = rad + 1;
					Pen.moveTo(mid + Point(0, d.neg));
					Pen.lineTo(mid + Point(d, 0));
					Pen.lineTo(mid + Point(0, d));
					Pen.lineTo(mid + Point(d.neg, 0));
					Pen.lineTo(mid + Point(0, d.neg));
				}
			)
		};
		if (fill.notNil) {
			Pen.fillColor = fill;
			path.(radius);
			Pen.fill;
		} {
			Pen.fillColor = c.colJackHole;
			path.(radius);
			Pen.fill;
			Pen.width = 2;
			Pen.strokeColor = c.colJackRing;
			path.(radius - 1);
			Pen.stroke;
		};
		if (wide) {
			Pen.width = 1;
			Pen.strokeColor = fill ? c.colJackRing;
			path.(radius + 2.5);
			Pen.stroke;
		};
		// a loose cable could land here
		if (ring) {
			Pen.width = 1.5;
			Pen.strokeColor = c.colAccent;
			path.(radius);
			Pen.stroke;
		};
	}

	// What the output zone says: "ar 1", "kr 4", "through ar 2" on a module
	// with a display - or the channels' names when the library module gives
	// them (outs:) and they fit beside one light: "value · gate".
	outputText {
		var plain = case
			{ module.rate.isNil } { "no bus yet" }
			{ display.notNil } { "through " ++ module.typeString }
			{ module.typeString };
		^module.outs !? { |names|
			var str = names.join(" · ");
			if (str.bounds(gui.font).width + 6 + 7 <= (size.x - 20 - outZone.left - 4)) { str }
		} ?? { plain }
	}

	// The output zone, the right half of the main row: the port on the right
	// edge, the rate and width before it, and a light per channel of a
	// control rate module, as bright as it is far from 0.
	prDrawOutput {
		var c = NdefRackGUI;
		var live = gui.liveValues(module.proxy);
		var text = this.outputText;
		var right = size.x - 20;
		var tw = text.bounds(gui.font).width;
		var lx = right - tw - 6;
		var shape = if (module.isAudio) { \circle } { \diamond };
		var rad = this.prPortRadius(false);
		// a loose cable from an input would come from here
		if (gui.dragTarget == [this.key, \out]) { this.prDrawTarget(outZone) };
		Pen.stringInRect(text, Rect(right - tw - 1, outJack.y - 8, tw + 2, 16), gui.font, c.colDim,
			QAlignment(\right));
		live !? {
			var n = ((lx - outZone.left - 4) div: 7).max(0);
			live.keep(n).do { |v, i|
				var lvl = v.abs.clip(0, 1);
				Pen.fillColor = c.colValue.copy.alpha_(0.15 + (0.85 * lvl));
				Pen.fillRect(Rect(lx - 5 - (i * 7), outJack.y - 2.5, 5, 5));
			}
		};
		this.prDrawShape(outJack, shape, rad, this.prRateColor, module.numChannels > 1, rad > portR);
		Pen.fillColor = this.prRateColor;
		Pen.fillRect(Rect(outJack.x + rad - 1, outJack.y - 1, size.x - outJack.x - rad + 1, 2));
	}

	// --------------------------------------------------------- the display

	prDrawDisplay {
		var c = NdefRackGUI;
		var rect = displayRect;
		Pen.fillColor = c.colTrack;
		Pen.addRoundedRect(rect, 3, 3);
		Pen.fill;
		if (display.isNil or: { display.isRunning.not }) {
			Pen.stringCenteredIn("no picture without a running server", rect,
				gui.font, c.colDim);
			^this
		};
		if (display.kind == \scope) { this.prDrawScope(rect) } { this.prDrawSpectrum(rect) };
	}

	prDrawScope { |rect|
		var c = NdefRackGUI;
		var traces = display.data;
		var gain = display.gain;
		var mid = rect.center.y;
		var half = rect.height / 2 - 2;
		var colors = [c.colValue, c.colAccent] ++ c.cableColors;
		Pen.width = 1;
		Pen.strokeColor = c.colGrid;
		[-0.5, 0.5].do { |f| Pen.line(Point(rect.left, mid + (f * half)), Point(rect.right, mid + (f * half))) };
		(1..3).do { |i| var x = rect.left + (rect.width * i / 4); Pen.line(Point(x, rect.top), Point(x, rect.bottom)) };
		Pen.stroke;
		Pen.strokeColor = c.colEdge;
		Pen.line(Point(rect.left, mid), Point(rect.right, mid));
		Pen.stroke;
		traces !? {
			traces.do { |vals, ch|
				var step = rect.width / (vals.size - 1).max(1);
				Pen.width = 1.3;
				Pen.strokeColor = colors.wrapAt(ch);
				vals.do { |v, i|
					var p = Point(rect.left + (i * step), (mid - (v * gain * half)).clip(rect.top, rect.bottom));
					if (i == 0) { Pen.moveTo(p) } { Pen.lineTo(p) };
				};
				Pen.stroke;
			};
		};
		Pen.stringInRect("%  x%".format(this.prSeconds(display.time), gain.round(0.01)),
			rect.insetBy(4, 2), gui.font, c.colDim, QAlignment(\topRight));
	}

	prDrawSpectrum { |rect|
		var c = NdefRackGUI;
		var db = display.data;
		var floor = display.floor;
		var nyquist = display.binFreq(NdefRackDisplay.fftSize div: 2 - 1);
		var lo = 20;
		var span = (nyquist / lo).log;
		var w = rect.width.asInteger.max(1);
		var cols = Array.newClear(w);
		var xOf = { |f| rect.left + ((f / lo).log / span * rect.width) };
		var yOf = { |v| rect.bottom - (((v - floor) / floor.neg).clip(0, 1) * rect.height) };
		var line = [];
		Pen.width = 1;
		Pen.strokeColor = c.colGrid;
		[100, 1000, 10000].do { |f| if (f < nyquist) { Pen.line(Point(xOf.(f), rect.top), Point(xOf.(f), rect.bottom)) } };
		(floor.neg / 20).floor.asInteger.do { |i|
			var y = yOf.(i.neg * 20);
			Pen.line(Point(rect.left, y), Point(rect.right, y));
		};
		Pen.stroke;
		[100, 1000, 10000].do { |f|
			if (f < nyquist) {
				Pen.stringAtPoint(if (f >= 1000) { "%k".format(f div: 1000) } { f.asString },
					Point(xOf.(f) + 2, rect.bottom - 15), gui.font, c.colDim)
			}
		};
		db !? {
			// many bins to a pixel at the top, many pixels to a bin at the
			// bottom: the loudest bin of each column, joined up
			db.do { |v, i|
				var f = display.binFreq(i);
				var col;
				if (f >= lo) {
					col = ((f / lo).log / span * w).floor.asInteger.clip(0, w - 1);
					cols[col] = cols[col] !? { |old| old.max(v) } ? v;
				};
			};
			cols.do { |v, x| v !? { line = line.add(Point(rect.left + x, yOf.(v))) } };
			if (line.size > 1) {
				Pen.fillColor = c.colValue.copy.alpha_(0.18);
				Pen.moveTo(Point(line.first.x, rect.bottom));
				line.do { |p| Pen.lineTo(p) };
				Pen.lineTo(Point(line.last.x, rect.bottom));
				Pen.fill;
				Pen.width = 1.3;
				Pen.strokeColor = c.colValue;
				Pen.moveTo(line.first);
				line.drop(1).do { |p| Pen.lineTo(p) };
				Pen.stroke;
			};
		};
		Pen.stringInRect("% dB".format(floor.round), rect.insetBy(4, 2), gui.font, c.colDim,
			QAlignment(\topRight));
	}

	prSeconds { |t|
		^if (t < 1) { "% ms".format((t * 1000).round(if (t < 0.01) { 0.1 } { 1 })) } { "% s".format(t.round(0.01)) }
	}

	// ----------------------------------------------------------------- hints

	// What the drop-down of a bus module says: the BusManager name it reads
	// or writes to, with a ? after it while BusManager knows no bus of that
	// name - bus 16 for an index of your own, with its width and rate - or
	// that it has none yet.
	busLabel {
		var bus = module.bus;
		var info = module.busInfo;
		^case
		{ bus.isNil } { "pick a bus" }
		{ bus.isNumber } {
			"bus %  % %".format(bus, if (info[\rate] == \control) { "kr" } { "ar" }, info[\channels])
		}
		{ this.prBusKnown } { bus.asString }
		{ bus.asString ++ " ?" }
	}

	// and what the hint bar says of it: reads, writes to, or reads and writes
	busText {
		var verb = module.busVerb;
		var writes = module.writesBus;
		var info = module.busInfo;
		var bm = \BusManager.asClass;
		var name = info !? { info[\name] };
		var index = info !? { info[\index] };
		var found = if (name.notNil and: { bm.notNil }) { bm.at(name) };
		var channels = { |n| "% channel%".format(n, if (n == 1) { "" } { "s" }) };
		^case
		{ name.isNil and: { index.isNil } } {
			"% no bus yet: pick one of BusManager's, or a bus index".format(verb)
		}
		{ name.isNil } {
			"% bus %, % rate, % - an index of its own, not one of BusManager's names"
				.format(verb, index, info[\rate], channels.(info[\channels]))
		}
		{ found.notNil } {
			"% BusManager's %: bus %, % rate, %".format(verb, name, found.index,
				found.rate, channels.(found.numChannels))
		}
		{ index.notNil } {
			// a writer goes to its stand-in when the name goes; only a reader
			// stays where it was
			"% bus %, where BusManager's % was - it knows no bus of that name now"
				.format(verb, index, name)
		}
		{ writes and: { verb == "reads and writes" } } {
			"silent, and writes nowhere: BusManager knows no bus called % - it is read and written once there is one".format(name)
		}
		{ writes } {
			"writes nowhere: BusManager knows no bus called % - it is written to once there is one".format(name)
		}
		{ "silent: BusManager knows no bus called % - it is read once there is one".format(name) }
	}

	// The drop-down of a module with speakers: the setup's label and how many
	// channels it has - the name with a ? after it while NdefRackSpeakers has
	// no setup of that name, or other speakers under it.
	speakersLabel {
		var info = module.speakerInfo;
		^case
		{ info.isNil } { "pick the speakers" }
		{ module.speakersKnown } { "%   %".format(info[\setup].label, info[\setup].numChannels) }
		{ "% ?".format(info[\name]) }
	}

	// and what the hint bar says of it
	speakersText {
		var info = module.speakerInfo;
		var setup = info !? { info[\setup] };
		var where;
		if (setup.isNil) { ^"no speakers yet" };
		where = "% speaker%%".format(setup.numSpeakers, if (setup.numSpeakers == 1) { "" } { "s" },
			if (setup.lfe.notNil) { " and a sub on channel %".format(setup.lfe + 1) } { "" });
		^case
		{ module.speakersKnown } { "pans among %: %, a channel each".format(setup.label, where) }
		{ setup.name == info[\name] } {
			"pans among %, as it was: % - NdefRackSpeakers has no such setup now".format(info[\name], where)
		}
		{
			"pans among % (%) until NdefRackSpeakers has a setup called %".format(setup.label, where, info[\name])
		}
	}

	prHasDrop { ^module.canBus or: { module.canSpeakers } }

	prBusKnown {
		var bm = \BusManager.asClass;
		var bus = module.bus;
		^bus.isKindOf(Symbol) and: { bm.notNil } and: { bm.includes(bus) }
	}

	// The hint for the cable into param, as the hint bar shows it with the
	// mouse over the cable itself.
	cableHint { |param|
		var r = rows.detect { |row| row[\name] == param };
		var src = module.source(param);
		^if (r.notNil and: { src.notNil }) { "% %".format(module.name, this.prCableHint(r, src)) }
	}

	// What the hint bar says about the point under the mouse. The cables run
	// behind the modules, so over a module the module answers.
	hintAt { |point|
		var name = module.name;
		var n = module.numChannels;
		var r;
		if (moreRect.notNil and: { moreRect.containsPoint(point) }) {
			^"% controls of % at their defaults, hidden - click to show every control".format(hiddenCount, name)
		};
		tabRects !? {
			tabRects.do { |r, i|
				if (r.containsPoint(point)) {
					var ps = pages[i][\bands].flat;
					var patched = ps.select { |p| module.isPatched(p[\name]) }.collect { |p| p[\name] };
					^"page % of %, %: %%".format(i + 1, pages.size, pages[i][\name],
						ps.collect { |p| p[\name] }.join(", "),
						if (patched.notEmpty) { " - patched: " ++ patched.join(", ") } { "" })
						++ if (i == page) { "" } { " - click to show it" }
				}
			};
			if (point.y >= (tabY - (tabH div: 2)) and: { point.y < (tabY + (tabH div: 2)) } and: { point.x < 24 }) {
				^"cables into controls on pages not shown end here"
			};
		};
		if (point.y < titleH) {
			if (closeRect.containsPoint(point)) {
				^"delete %: unplugs it, clears Ndef(%) - cmd-z brings it back".format(name, name.asCompileString)
			};
			if (compactRect.containsPoint(point)) {
				^if (this.isCompact) { "show every control of %".format(name) } {
					"compact %: only the controls set away from their defaults, or patched".format(name)
				}
			};
			if (module.isAudio and: { playRect.containsPoint(point) }) {
				^if (module.isMonitoring) { "stop playing %".format(name) } {
					"play % - %".format(name, case
						{ n == 1 } { "a mono module on both sides" }
						{ n == 2 } { "left and right" }
						{ NdefRack.prToSpeakers(module, n) } { "each of its % speakers on its own output, 1 to %".format(n, n) }
						{ module.canSpeakers } {
							"its % speakers folded onto the stereo pair: the server has % outputs"
								.format(n, NdefRack.server.options.numOutputBusChannels)
						}
						{ "its % channels spread across the stereo pair".format(n) })
				}
			};
			^this.prAbout ++ "%, %%% - drag to move, shift-click to select more, right click for %copy, delete and the rest"
				.format(name, module.typeString,
					module.kind !? { " (library %)".format(module.kind) } ? "",
					(if (module.canBus) { " - " ++ this.busText } { "" })
						++ (if (module.canSpeakers) { " - " ++ this.speakersText } { "" })
						++ (NdefRack.recordSource(module.name) !? { |src|
							" - recorded by BusRecorder as %%".format(src.name, if (src.armed) { "" } { ", disarmed" })
						} ? ""),
					case
					{ module.canVoice } { "voices (% now), ".format(module.voices) }
					{ module.canBus } { "another bus, " }
					{ module.canSpeakers } { "other speakers, " }
					{ "" })
		};
		if (displayRect.notNil and: { displayRect.containsPoint(point) }) { ^this.prDisplayHint };
		if (busRect.notNil and: { busRect.containsPoint(point) }) {
			^if (module.canSpeakers) {
				this.speakersText ++ " - click for the other setups"
			} {
				this.busText ++ " - click for BusManager's buses, or a bus index of your own"
			}
		};
		if (this.isOverOutput(point)) {
			^"the output of %, %%: drag from here to an input".format(name, module.typeString,
				case
				{ module.voices > 1 and: { n == module.voices } } { ", a channel per voice" }
				{ module.outs.notNil } {
					" - " ++ module.outs.collect { |s, i| "% on %".format(s, i + 1) }.join(", ")
				}
				{ "" })
		};
		r = this.prRowAt(point);
		if (r.isNil) { ^this.prAbout ++ "% - drag the empty face to move it".format(name) };
		if (NdefRackMapper.learning and: { this.prLearnable(r) }) { ^this.prLearnHint(r) };
		^(r[\param][\help] !? { |h| "%: %  ·  ".format(r[\name], h) } ? "") ++ this.prRowHint(r, point) ++ (NdefRackMapper.controlOf(name, r[\name]) !? { |k|
			" - %'s % turns it%".format(k[\ktl], k[\element], if (k[\follow]) { ", following the selection" } { "" })
		} ? "")
	}

	// What the library module does, ahead of the rest of a hint - "lfo: a low
	// frequency oscillator ...  ·  " - or nothing for a module of your own.
	prAbout {
		^module.kind !? { |k| NdefRackLib.about(k) } !? { |a|
			if (a.isEmpty) { "" } { "%: %  ·  ".format(module.kind, a) }
		} ? ""
	}

	prLearnHint { |r|
		var ktl = NdefRackMapper.controlOf(this.key, r[\name]);
		var now = ktl !? { " - %'s % turns it now".format(ktl[\ktl], ktl[\element]) } ? "";
		^if (NdefRackMapper.armed == [this.key, r[\name]]) {
			"move a knob, fader or button on the controller to turn %%; click again to stop, backspace forgets"
				.format(r[\name], now)
		} {
			"click, then move a knob, fader or button on the controller to turn %%".format(r[\name], now)
		}
	}

	prRowHint { |r, point|
		var name = r[\name];
		var p = r[\param];
		var spec = p[\spec];
		var src = module.source(name);
		var all = if (p[\voice] == true) { " - one control for all % voices".format(module.voices) } { "" };
		var range = { "range % to %".format(this.prReadout(r, spec.minval), this.prReadout(r, spec.maxval)) };
		if (src.notNil) { ^this.prCableHint(r, src) };
		if (r[\port].containsPoint(point)) {
			^"the % input: drag from here to an output, or from an output to here".format(name)
		};
		^switch(r[\role],
			\toggle, {
				"% %: click to switch it, double click for %%".format(name,
					this.prSwitchText(r, module.value(name).asArray.first ? 0 > 0.5),
					this.prSwitchText(r, p[\default].asArray.first ? 0 > 0.5), all)
			},
			\array, {
				var what = if (spec.step == 1) { "click a cell to flip it, drag across to set more" } {
					"click and drag across the bars"
				};
				if (p[\voice] == true) {
					"% - a value for each of the % voices: %".format(name, p[\size], what)
				} {
					"% - % values: %".format(name, p[\size], what)
				}
			},
			\signal, {
				"% - an audio input: patch a signal into the port%".format(name,
					if (p[\voice] == true) { ", one channel per voice" } { "" })
			},
			\trigger, { "% - click to fire it%".format(name, all) },
			\gate, { "% - 1 while you hold it down%".format(name, all) },
			{
				// \bar, \stepped
				"% %: drag left and right (shift for fine), click the value to type one, double click for %, %%"
					.format(name, this.prReadout(r, module.value(name)), this.prReadout(r, p[\default]),
						range.value, all)
			}
		)
	}

	// What a cable does here: replace the value - and with voices, which voice
	// gets which channel; a cable with voices into one input, that only the
	// first arrives. A wide cable that is not voices - turing's value and gate
	// - into one input brings its first channel, the main one, as it should.
	prCableHint { |r, src|
		var name = r[\name];
		var p = r[\param];
		var from = this.prSourceName(src);
		var ch = src.numChannels;
		var voices = module.voices;
		var srcVoices = NdefRack.prModuleKey(src) !? { |k| NdefRack.modules.at(k) } !? (_.voices) ? 1;
		var own = if (#[\audio, \array].includes(r[\type]).not) {
			"; returns to % when unplugged".format(this.prReadout(r, module.value(name)))
		} { "" };
		var off = " - driven by %: drag the port to move or unplug it%".format(from, own);
		^case
		{ module.isSpread(name) } {
			"% ← %: one signal, into all % voices%".format(name, from, voices, off)
		}
		{ p[\voice] == true } {
			case
			{ ch == voices } { "% ← %: voice by voice, % into %%".format(name, from, ch, voices, off) }
			{ ch < voices } {
				"% ← %: % channels into % voices - voices % to % keep their own value%"
					.format(name, from, ch, voices, ch + 1, voices, off)
			}
			{ "% ← %: % channels into % voices - the ones past % are left out%"
				.format(name, from, ch, voices, voices, off) }
		}
		{ ch > p[\size] and: { srcVoices > 1 } } {
			if (p[\size] == 1) {
				"% ← %: % voices into one input - only the first arrives; patch through splay or sum to hear them all%"
					.format(name, from, srcVoices, off)
			} {
				"% ← %: % voices into % - only the first % arrive%".format(name, from, srcVoices, p[\size], p[\size], off)
			}
		}
		{ ch > p[\size] } {
			"% ← %: the cable replaces the value, with the first %of its % channels%"
				.format(name, from, if (p[\size] == 1) { "" } { "% ".format(p[\size]) }, ch, off)
		}
		{ "% ← %: the cable replaces the value%".format(name, from, off) }
	}

	prDisplayHint {
		^if (display.kind == \scope) {
			"the scope: what comes into in, % across (time), x% (gain), triggered as channel 1 rises through 0"
				.format(this.prSeconds(display.time), display.gain.round(0.01))
		} {
			"the spectrum of what comes into in: 20 Hz to the top across, % dB to 0 dB up (floor)"
				.format(display.floor.round)
		}
	}

	// --------------------------------------------------------------- helpers

	prRateColor {
		var c = NdefRackGUI;
		^case
		{ module.isAudio } { c.colAudio }
		{ module.isControl } { c.colControl }
		{ c.colDim }
	}

	prSourceName { |src|
		^NdefRack.prModuleKey(src) !? (_.asString) ?? { src.asString }
	}

	// ---------------------------------------------------------------- mouse

	prMouseDown { |point, mod, button, clicks|
		var r;
		drag = nil;
		// a cable armed by a click: this click lands it - a right click lets
		// it go. In learn mode it goes, and the click does what it does there.
		if (gui.armedCable.notNil) {
			if (NdefRackMapper.learning) { gui.prCableCancel } {
				if (button == 1 or: { mod.isCtrl }) { gui.prCableCancel } { gui.prArmedClick(this.origin + point) };
				^true
			}
		};
		// a right click, or control-click the way a Mac trackpad has it
		if (button == 1 or: { mod.isCtrl }) {
			this.prMenu(point);
			^true
		};
		if (point.y < titleH) {
			if (closeRect.containsPoint(point)) {
				gui.deleteModules([this.key]);
				^true
			};
			if (compactRect.containsPoint(point)) {
				gui.setCompact(gui.actionKeys(this.key), this.isCompact.not);
				^true
			};
			if (module.isAudio and: { playRect.containsPoint(point) }) {
				NdefRack.togglePlay(this.key);
				^true
			};
			this.prBeginMove(point, mod);
			^true
		};
		if (moreRect.notNil and: { moreRect.containsPoint(point) }) {
			gui.setCompact([this.key], false);
			^true
		};
		tabRects !? {
			tabRects.do { |r, i|
				if (r.containsPoint(point)) {
					if (i != page) { NdefRack.setPage(this.key, i) };
					^true
				}
			}
		};
		if (this.isOverOutput(point)) {
			gui.prCableFromOutput(this.key, this.origin + point);
			drag = (mode: \cable);
			^true
		};
		if (busRect.notNil and: { busRect.containsPoint(point) }) {
			this.prDropMenu.front;
			^true
		};
		r = this.prRowAt(point);
		if (r.isNil) {
			// the plain face - and the display - move the module as the title does
			this.prBeginMove(point, mod);
			^true
		};
		// learning, a click anywhere on a control arms it - again lets go
		if (NdefRackMapper.learning and: { this.prLearnable(r) }) {
			if (NdefRackMapper.armed == [this.key, r[\name]]) {
				NdefRackMapper.cancelLearn
			} {
				NdefRackMapper.learn(this.key, r[\name])
			};
			^true
		};
		if (r[\port].containsPoint(point)) {
			gui.prCableFromInput(this.key, r[\name], this.origin + point);
			drag = (mode: \cable);
			^true
		};
		// the strip between the port and the control belongs to nothing
		if (point.x < 30 and: { point.y < r[\port].bottom }) { ^true };
		// a patched control is driven by its cable: dead to the mouse
		if (module.isPatched(r[\name])) { ^true };
		switch(r[\role],
			\signal, { },
			\array, {
				// the label line above the bars does nothing
				if (point.y >= r[\bars].top) {
					drag = (mode: \array, row: r, last: nil, paint: nil);
					this.prArraySet(r, point);
				}
			},
			\trigger, { this.prPress(r) },
			\gate, { this.prPress(r) },
			{
				// \bar, \stepped, \toggle: a drag left and right moves the value;
				// a click types one on the value text, flips a switch; a double
				// click away from the value text resets
				if (clicks == 2) {
					if (r[\role] == \toggle or: { this.prValueRect(r).insetBy(-3, 0).containsPoint(point).not }) {
						module.reset(r[\name]);
					};
					drag = (mode: \none);
					^true
				};
				// every voice as it was when the drag began, in unmapped space:
				// the drag moves them all by one offset, so the spread between
				// them - lin 200 800, a chord - is kept (a ratio on an exp spec)
				drag = (mode: \bar, row: r, start: point, x: point.x, moved: false,
					norm: r[\param][\spec].unmap(module.value(r[\name]).asArray.first ? 0),
					base: r[\param][\spec].unmap(module.value(r[\name]).asArray), offset: 0,
					onValue: (r[\role] != \toggle and: {
						this.prValueRect(r).insetBy(-3, 0).containsPoint(point) }));
			}
		);
		^true
	}

	// a gate or a trigger held down: 1 until it is let go
	prPress { |r|
		pressed = r[\name];
		module.set(r[\name], 1);
		drag = (mode: \button, row: r);
		this.refresh;
	}

	prMouseMove { |point, mod|
		if (drag.isNil) { ^true };
		switch(drag[\mode],
			\move, { gui.prGroupMove(this.origin + point) },
			\cable, { gui.prCableMove(this.origin + point) },
			\bar, {
				// relative: the bar's width is the whole range, shift ten times
				// finer; nothing before the mouse has moved 4 px, so a click
				// stays a click
				var r = drag[\row];
				var spec = r[\param][\spec];
				var scale = if (mod.isShift) { 0.1 } { 1 };
				if (drag[\moved].not and: { (point - drag[\start]).rho >= 4 }) { drag[\moved] = true };
				if (drag[\moved]) {
					var base = drag[\base];
					var dx = (point.x - drag[\x]) / r[\bar].width * scale;
					drag[\x] = point.x;
					if (base.size > 1 and: { base.every { |b| b == base.first }.not }) {
						// voices apart: one offset for all, stopping once every
						// voice is at an end; each clipped on its own, and the
						// offset counted from the start, so dragging back
						// gives the spread back
						drag[\offset] = (drag[\offset] + dx).clip(base.maxItem.neg, 1 - base.minItem);
						module.set(r[\name], spec.map((base + drag[\offset]).clip(0, 1)));
					} {
						drag[\norm] = (drag[\norm] + dx).clip(0, 1);
						module.set(r[\name], spec.map(drag[\norm]));
					};
					this.refresh;
				};
			},
			\array, { this.prArraySet(drag[\row], point) }
		);
		^true
	}

	prMouseUp { |point, mod|
		drag !? {
			switch(drag[\mode],
				\move, { gui.prGroupMoveEnd(this.key, drag[\shift]) },
				\cable, { gui.prCableEnd(this.origin + point) },
				\bar, {
					if (drag[\moved].not) {
						case
						{ drag[\row][\role] == \toggle } {
							module.set(drag[\row][\name],
								if (module.value(drag[\row][\name]).asArray.first ? 0 > 0.5) { 0 } { 1 });
						}
						{ drag[\onValue] } { this.prEditValue(drag[\row]) };
						this.refresh;
					}
				},
				\button, {
					// let go: the default back, and the node map clean of a 0
					// the code export would otherwise write down
					module.reset(drag[\row][\name]);
					pressed = nil;
					// a trigger flashes a moment after it fired
					if (drag[\row][\role] == \trigger) {
						flash = flash ?? { IdentityDictionary.new };
						flash[drag[\row][\name]] = Main.elapsedTime + 0.15;
						{ this.refresh }.defer(0.16);
					};
					this.refresh;
				}
			);
		};
		drag = nil;
		// a compact module waits for the drag to end before it lays out again
		if (shown.notNil) { gui.prStateChanged(this.key) };
		^true
	}

	// A click on the title or the face selects: on its own, or with shift
	// added to - or taken out of - what is selected. A drag then moves every
	// selected module together; a module shift-clicked out of the selection
	// does not move.
	prBeginMove { |point, mod|
		var shift = mod.isShift;
		gui.prClickSelect(this.key, shift);
		if (gui.isSelected(this.key)) {
			drag = (mode: \move, shift: shift);
			gui.prGroupMoveBegin(this.origin + point);
		};
	}

	// a drag across the bars sets every bar it passes, not only the ones
	// the mouse happened to be over when an event arrived. Toggle cells: the
	// press flips its cell, and the drag sets the others it passes the same.
	prArraySet { |r, point|
		var bars = r[\bars];
		var spec = r[\param][\spec];
		var values = module.value(r[\name]).asArray.copy;
		var n = values.size;
		var i = ((point.x - bars.left) / bars.width * n).floor.clip(0, n - 1).asInteger;
		var v = if (spec.step == 1) {
			drag !? { drag[\paint] } ?? {
				var flipped = if (values[i] > 0.5) { spec.clipLo } { spec.clipHi };
				drag !? { drag[\paint] = flipped };
				flipped
			}
		} {
			spec.map((1 - ((point.y - bars.top) / bars.height)).clip(0, 1))
		};
		var from = drag !? { drag[\last] } ? i;
		(min(from, i)..max(from, i)).do { |j| values[j] = v };
		drag !? { drag[\last] = i };
		module.set(r[\name], values);
		this.refresh;
	}

	// ----------------------------------------------------------------- menus

	prMenu { |point|
		var r = if (point.y >= titleH) { this.prRowAt(point) };
		var actions;
		if (busRect.notNil and: { busRect.containsPoint(point) }) { ^this.prDropMenu.front };
		actions = if (r.isNil) { this.prModuleActions } { this.prParamActions(r) };
		Menu(*actions.asArray).front;
	}

	// The module's own menu - or the selection's, when this module is part of
	// one: copy three, delete three.
	prModuleActions {
		var acts = List.new;
		var key = this.key;
		var keys = gui.actionKeys(key);
		var what = if (keys.size > 1) { "% modules".format(keys.size) } { key.asString };
		var polys = keys.select { |k| NdefRack.canVoice(k) };
		var recs = keys.select { |k| NdefRack.canRecord(k) };
		if (module.isAudio and: { keys.size == 1 }) {
			acts.add(MenuAction(if (module.isMonitoring) { "stop" } { "play" },
				{ NdefRack.togglePlay(key) }));
		};
		// BusRecorder's sources: the selection's audio modules, all at once
		if (recs.notEmpty) {
			acts.add(MenuAction("record %".format(if (recs.size > 1) { "% modules".format(recs.size) } { recs[0] }), { |a|
				gui.setRecorded(recs, recs.every { |k| NdefRack.isRecorded(k) }.not)
			}).checked_(recs.every { |k| NdefRack.isRecorded(k) }));
		};
		// which bus a bus module reads or writes to
		if (module.canBus and: { keys.size == 1 }) {
			acts.add(this.prBusMenu.title_("bus"));
		};
		// which speakers a panner pans among
		if (module.canSpeakers and: { keys.size == 1 }) {
			acts.add(this.prSpeakersMenu.title_("speakers"));
		};
		// the selection's modules that can have voices, all set at once - the
		// voices given them; a wider cable in plays more
		if (polys.notEmpty) {
			acts.add(Menu(*NdefRackGUI.voiceChoices.collect { |n|
				MenuAction(n.asString, { gui.setVoices(polys, n) })
					.checked_(polys.every { |k| NdefRack.voiceFloor(k) == n })
			}).title_("voices"));
		};
		acts.add(MenuAction("compact - only what is not at its default", {
			gui.setCompact(keys, keys.every { |k| NdefRack.isCompact(k) }.not)
		}).checked_(keys.every { |k| NdefRack.isCompact(k) }));
		// a colour to mark them with: the edge and the title take it
		acts.add(gui.colorMenu(if (keys.size == 1) { NdefRack.colorOf(key) }, { |col|
			keys.do { |k| NdefRack.setColor(k, col) }
		}).title_("colour"));
		if (keys.size > 1) {
			acts.add(MenuAction("arrange %   a".format(what), { gui.arrangeModules(keys) }))
		};
		acts.add(MenuAction("network box round %   cmd-g".format(what), {
			gui.select(keys);
			gui.boxSelection
		}));
		acts.add(MenuAction("copy %   cmd-c".format(what), { gui.copyModules(keys) }));
		acts.add(MenuAction("cut %   cmd-x".format(what), { gui.cutModules(keys) }));
		acts.add(MenuAction("duplicate %   cmd-d".format(what), { gui.duplicateModules(keys) }));
		acts.add(MenuAction("% as code".format(what), { NdefRack.patchDocument(keys) }));
		acts.add(MenuAction.separator);
		acts.add(MenuAction("unplug every cable of %".format(what), { keys.do { |k| NdefRack.unplug(k) } }));
		if (keys.size == 1) {
			acts.add(MenuAction("open in NdefGui", { module.proxy.edit }));
			module.kind !? { |k|
				acts.add(MenuAction("about % - every control, in the post window".format(k), { NdefRackLib.postHelp(k) }));
				acts.add(MenuAction("open the % module file".format(k), { NdefRackLib.openFile(k) }));
			};
		};
		acts.add(MenuAction.separator);
		acts.add(MenuAction("delete %   backspace".format(what), { gui.deleteModules(keys) }));
		^acts
	}

	prParamActions { |r|
		var acts = List.new;
		var key = this.key, name = r[\name];
		var p = r[\param];
		var info;
		if (module.isPatched(name)) {
			acts.add(MenuAction("unplug %".format(name), { NdefRack.disconnect(key, name) }));
		} {
			if (r[\type] != \audio) {
				acts.add(MenuAction("reset to %".format(
					if (p[\voice] == true and: { r[\type] != \array }) { p[\default].asArray.first } { p[\default] }),
					{ module.reset(name) }));
				acts.add(MenuAction("type a value", { this.prEditValue(r) }));
				// a value per voice shown as a slider per voice, or as one bar again
				if (p[\voice] == true and: { module.voices > 1 }
					and: { NdefRackLib.sliders(module.kind).includes(name).not }
					and: { #[\trigger, \gate].includes(r[\type]).not }) {
					acts.add(MenuAction("a slider per voice", {
						NdefRack.showVoices(key, name, NdefRack.showsVoices(key, name).not)
					}).checked_(NdefRack.showsVoices(key, name)));
				};
				// a value per voice: the field, filled in with a generator to edit
				if (module.canVoice and: { NdefRackLib.voiceKeys(module.kind).includes(name) }
					and: { #[\trigger, \gate].includes(r[\type]).not }) {
					// applied at once; a template to finish (a function of
					// the voice) opens the field instead
					acts.add(Menu(*this.prSpreadTemplates(r).collect { |item|
						if (item[2] == \edit) {
							MenuAction(item[0] ++ "...", { this.prEditValue(r, item[1]) })
						} {
							MenuAction("%   %".format(item[0], item[1]),
								{ NdefRack.setValues(key, name, item[1]) })
						}
					}).title_("per voice"));
				};
			};
		};
		// a controller on it: learn, Mapper's window on it, forget
		if (this.prLearnable(r) and: { NdefRackMapper.canLearn }) {
			acts.add(this.prMidiMenu(r));
		};
		acts.add(MenuAction("the set line as code", {
			var v = module.source(name) ?? { module.value(name) };
			NdefRack.codeDocument("%.set(%, %);".format(NdefRack.prNdefString(key), NdefRack.prSym(name),
				if (v.isKindOf(NodeProxy)) { NdefRack.prSourceString(v) } {
					NdefRack.prValueString(v)
				}), "ndef rack - %".format(key));
		}));
		info = MenuAction("range % to %, %".format(p[\spec].minval, p[\spec].maxval,
			p[\spec].warp.asSpecifier), { });
		info.enabled = false;
		acts.add(info);
		^acts
	}

	// What turns a control and what can: learn the next element moved - in
	// Mapper when it is installed -, the mapping in Mapper's window, forget it
	prMidiMenu { |r|
		var key = this.key, name = r[\name];
		var ktl = NdefRackMapper.controlOf(key, name);
		var waiting = NdefRackMapper.armed == [key, name];
		var items = [
			MenuAction(ktl !? { "turned by %'s %".format(ktl[\ktl], ktl[\element]) } ? "no controller turns it")
				.enabled_(false),
			if (waiting) {
				MenuAction("stop waiting for the controller", { NdefRackMapper.cancelLearn })
			} {
				MenuAction("learn: move a control next", { NdefRackMapper.learn(key, name) })
			}
		];
		if (NdefRackMapper.usesMapper) {
			items = items.add(MenuAction("edit in Mapper...", { NdefRackMapper.gui(key, name) })
				.enabled_(ktl !? { ktl[\mapper] == true } ? false));
		};
		items = items.add(MenuAction("forget the controller", { NdefRackMapper.forget(key, name) })
			.enabled_(ktl.notNil and: { ktl[\follow] != true }));
		^Menu(*items).title_("controller")
	}

	// A bus module's buses: BusManager's, the one it reads or writes to
	// ticked, and its window last, to add one - between them a bus index of
	// your own, typed in, and while it has one, its width and rate.
	prBusMenu {
		var key = this.key;
		var bm = \BusManager.asClass;
		var info = module.busInfo ? ();
		var own = info[\name].isNil and: { info[\index].notNil };
		var items = (bm !? { bm.names } ? []).collect { |n|
			var e = bm.at(n);
			MenuAction("%   % %".format(n, if (e.rate == \control) { "kr" } { "ar" }, e.numChannels),
				{ NdefRack.setBus(key, n) }).checked_(n == info[\name])
		};
		if (items.isEmpty) {
			items = [MenuAction(if (bm.isNil) { "BusManager is not installed" } { "BusManager has no buses yet" })
				.enabled_(false)]
		};
		items = items ++ [
			MenuAction.separator,
			MenuAction(if (own) { "bus index %...".format(info[\index]) } { "a bus index..." },
				{ this.prEditBus }).checked_(own)
		];
		if (own) {
			items = items ++ [
				Menu(*[1, 2, 3, 4, 6, 8, 16].collect { |n|
					MenuAction(n.asString, { NdefRack.setBus(key, info[\index], n) })
						.checked_(n == info[\channels])
				}).title_("channels"),
				Menu(*[\audio, \control].collect { |rate|
					MenuAction(rate.asString, { NdefRack.setBus(key, info[\index], nil, rate) })
						.checked_(rate == info[\rate])
				}).title_("rate")
			]
		};
		bm !? { items = items ++ [MenuAction.separator, MenuAction("the bus manager window", { bm.gui })] };
		^Menu(*items)
	}

	prDropMenu { ^if (module.canSpeakers) { this.prSpeakersMenu } { this.prBusMenu } }

	// The speaker setups: those that come with the rack, then your own, the
	// module's ticked - and its name alone, greyed, while the table has no
	// such setup.
	prSpeakersMenu {
		var key = this.key;
		var info = module.speakerInfo ? ();
		var known = module.speakersKnown;
		var item = { |n|
			var setup = NdefRackSpeakers.at(n);
			MenuAction("%   %".format(setup.label, setup.numChannels), { NdefRack.setSpeakers(key, n) })
				.checked_(known and: { n == info[\name] })
		};
		var own = NdefRackSpeakers.userNames;
		var items = NdefRackSpeakers.names.reject { |n| own.includes(n) }.collect(item);
		if (own.notEmpty) { items = items ++ [MenuAction.separator] ++ own.collect(item) };
		if (known.not and: { info[\name].notNil }) {
			items = items ++ [MenuAction.separator,
				MenuAction("% ?   NdefRackSpeakers.add makes it".format(info[\name])).enabled_(false).checked_(true)]
		};
		^Menu(*items)
	}

	// A field over the drop-down: a bus index, at the width and rate the
	// module has now - or a Bus, at its own.
	prEditBus {
		var bus = module.bus;
		this.prField(busRect.insetBy(-2, -1), if (bus.isNumber) { bus.asString } { "" }, { |str|
			var v = try { str.interpret };
			case
			{ v.isNumber and: { v >= 0 } } { NdefRack.setBus(this.key, v.asInteger) }
			{ v.isKindOf(Bus) } { NdefRack.setBus(this.key, v) }
			{ ("NdefRack: % is not a bus index".format(str.asCompileString)).warn };
		});
	}

	// A field over the value text - over the row for an array: a number, an
	// array, any expression giving one, a function of the voice or a
	// generator (NdefRack.setValues). text fills it in.
	// [label, text] for the per voice menu: each generator, with numbers
	// from the control's own range and value
	prSpreadTemplates { |r|
		var spec = r[\param][\spec];
		var v = module.value(r[\name]).asArray.first ? 0;
		var lo = spec !? { spec.map(0.25) } ? 0, hi = spec !? { spec.map(0.75) } ? 1;
		var w = spec !? { (spec.maxval - spec.minval).abs * 0.05 } ? 0.1;
		var f = { |x| NdefRack.prValueString(x) };
		^[
			["evenly, lin", "lin % %".format(f.(lo), f.(hi))],
			["by ratio, exp", "exp % %".format(f.(lo.max(0.001)), f.(hi.max(0.002)))],
			["around the value, spread", "spread % %".format(f.(v), f.(w))],
			["random, rand", "rand % %".format(f.(lo), f.(hi))],
			["harmonics, harm", "harm %".format(f.(v))],
			["alternating, alt", "alt % %".format(f.(lo), f.(hi))],
			["a chord in octaves, chord", "chord 0 4 7"],
			["a function of the voice", "{ |i, n| % }".format(f.(v)), \edit],
			["the same on every voice", f.(v)]
		]
	}

	prEditValue { |r, text|
		var rect = r[\bar] !? { |bar|
			var vr = this.prValueRect(r);
			var w = vr.width.max(84) + 8;
			Rect((vr.right + 4 - w).max(bar.left), bar.top - 2, w.min(bar.width), bar.height + 4)
		} ?? { Rect(30, r[\rect].top + 1, size.x - 38, 22) };
		// the expression it came from while it still holds what that gave -
		// lin 200 800, { |i, n| ... } - else the value
		this.prField(rect,
			text ?? { NdefRack.valuesText(this.key, r[\name]) } ?? { NdefRack.prValueString(module.value(r[\name])) },
			{ |str| NdefRack.setValues(this.key, r[\name], str) });
	}

	// a text field over the view, gone once it is entered or loses focus;
	// action gets what was typed
	prField { |rect, string, action|
		var done = false;
		var finish = { |field|
			if (done.not) {
				done = true;
				{ if (field.isClosed.not) { field.remove } }.defer(0.01);
				editField = nil;
			}
		};
		editField !? { if (editField.isClosed.not) { editField.remove } };
		editField = TextField(view, Rect(rect.left * gui.zoom, rect.top * gui.zoom, rect.width * gui.zoom, rect.height * gui.zoom))
			.font_(Font(gui.font.name, gui.font.size * gui.zoom))
			.string_(string)
			.action_({ |field|
				action.value(field.string);
				finish.(field);
			})
			.focusLostAction_({ |field| finish.(field) });
		editField.focus(true);
	}
}
