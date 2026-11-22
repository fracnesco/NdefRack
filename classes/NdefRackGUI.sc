// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackGUI - the rack window
//
//   NdefRack.gui;
//
//  ndef rack [modules] [+ module] [arrange] [+ comment]  [-] [100%] [+] [fit] [centre]
//    [patch as code] [save patch] [load patch v]
//    [learn] [mapper]   [rec] [recorder]           cables == [flow chart v]
//  +----------------+      +----------------+      +----------------+
//  |<> lfo   lfo [x]|      |[>] osc wave [x]|      |[>] flt  lpf [x]|
//  |       .. kr 1 <>---.  o in        ar 1 o------o in        ar 1 o
//  |<> [rate  4 Hz] |   '--<> [oct  +0.50]  |      <> [freq 1.2 kHz]|
//  +----------------+      +----------------+      +----------------+
//  9 modules · 6 cables · server localhost running               [x] hints
//  hover over anything in the rack and this bar says what it does
//
// Modules are UserViews on a scrolling canvas. The cables are drawn on one
// transparent view made before every module and never brought forward, so
// they run behind the modules and never cover a label, a value or a port:
// from an output on a right edge to an input on a left edge, orange for
// audio, blue for control - as a flow chart by default, straight runs at
// right angles round the modules (NdefRackRouter), else as flat curves or
// straight lines. The cables of the selected modules,
// the one under the mouse and the one being dragged are drawn again on a
// transparent view above them all, so a selected module's cables can be
// followed end to end.
//
// The window keeps nothing of the patch: it listens to NdefRack - the
// modules there are, their state, the cables - marks what changed, and
// redraws that at the next frame. What it does keep is its own: the
// selection, the drag in progress. Thirty frames a second it also reads
// the output of every control rate module from the server's shared
// memory, and asks every scope and spectrum for its picture.
//
// Rest the mouse on a cable for half a second and a small picture of what it
// carries opens beside it: a row per channel - the wave of an audio cable,
// the last two seconds of a control one. Moving the mouse closes it, and so
// does the cable going.
//
//   NdefRackGUI.cableStyle = \curved;    // \routed (the default), \straight
//
//   click, shift-click, drag on the empty rack   select
//   drag a selected module                       move the selection
//   backspace, delete                            delete the selection
//   cmd-c  cmd-x  cmd-v  cmd-d                   copy, cut, paste, duplicate
//   cmd-a  escape                                select all, select nothing
//   cmd-plus  cmd-minus  cmd-0, cmd-wheel         zoom in, out, 100%
//   f  h                                         fit everything, back to the middle
//   + comment, or comment here on the rack menu  a box of text (double click
//                                                writes, the corner resizes)
//   cmd-g, or the menus                          a network box round the selection:
//                                                drag its title to move it and all
//                                                in it, the corner resizes it
//   right click a module, a cable, a box: colour mark it - a module's edge and
//                                                title, stripes on a cable
//   cmd-z                                        bring the last delete back
//   cmd-b, the modules button                    the module library down the left
//                                                (NdefRackBrowser): search, sort,
//                                                double click or drag a name in
//   cmd-f                                        its search
//   drag from an output to a row or a title      a cable (escape lets it go)
//   click an output or an input, then a target   a cable, no button held
//   click a cable on the empty rack              select it (backspace unplugs it)
//   drag it, flow chart only                     move where it bends (shift: free)
//   double click it                              the window finds its way again
//   right click a cable on the empty rack        unplug it
//   learn, then click a control and move a       a controller turns it
//   controller element (Mapper, or NdefRackKtl)  (backspace forgets, escape stops)
//   mapper                                       Mapper's window, when installed
//
// Copying takes the cables between the copied modules along; a cable from
// or to anything outside the copy stays behind. See NdefRack.copy.
//
// Opening the window starts NdefRack watching; closing it stops the
// watching again, unless something else had started it first.
// =====================================================================

NdefRackGUI {
	classvar <colBg, <colGrid, <colPanel, <colEdge, <colTitle, <colText, <colDim;
	classvar <colAccent, <colAudio, <colControl, <colKnob, <colTrack, <colValue;
	classvar <colJackRing, <colJackHole, <cableColors;
	classvar <>cableAlpha = 1.0, <>showReads = true, <>frameRate = 30;
	classvar <>voiceChoices = #[1, 2, 3, 4, 5, 6, 8, 12, 16];
	// how cables are drawn: \routed - a flow chart, straight runs at right
	// angles round the modules, bends dragged where you want them - \curved
	// or \straight; the toolbar's cables menu
	classvar <>cableStyle = \routed;
	classvar cableStyles;
	// the colours a module, a cable or a network box can be marked with, by
	// name - none of them audio's orange or control's blue
	classvar <palette, <paletteNames;
	// the zoom steps the toolbar's - and + and cmd-wheel go through
	classvar <>zoomSteps = #[0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1, 1.1, 1.25, 1.5, 2];
	// frames of a control signal the preview over a cable shows: 2 s at 30 fps
	classvar <>historyLength = 60;
	// seconds the mouse rests on a cable before its preview opens
	classvar <>previewDelay = 0.5;

	var <window, scroll, <canvas, boxLayer, cableLayer, overlay, <views, <hints, zOrder, layerKey;
	var <selectedBox, boxDrag, dirtyBoxes = true, <boxField, boxNaming, boxDraft;
	var <font, <boldFont;
	var ctrl, updater, wasWatching;
	var cableCache, inputCache, liveCache, displaysDue;
	var <dragTarget, cableDrag, band, groupMove;
	var selection;
	var dirtyAll = true, dirtyCables = true, dirtyModules, dirtyState, dirtyMonitor;
	var statusText, patchMenu, patchPaths, lastStatus;
	var styleMenu, drawnStyle;
	var routeCache, <selectedCable, bendDrag;
	var <zoom = 1, zoomButton;
	var <commentViews, <selectedComment, dirtyComments = true;
	var hover, hoverOverride, restPoint, restSince = 0;
	var preview, previewPoint, previewView, previewShown = false;
	var <probe, probeIdle, <liveHistory;
	var recCtrl, recButton, recDirty = true;
	var ktlCtrl, mapperCtrl, learnButton, ktlDirty = true;
	var movedAt;
	var <browser, browserButton, <libDrag;
	classvar <colRec;

	*initClass {
		colBg       = Color(0.105, 0.105, 0.12);
		colGrid     = Color(0.16, 0.16, 0.185);
		colPanel    = Color(0.205, 0.205, 0.235);
		colEdge     = Color(0.31, 0.31, 0.36);
		colTitle    = Color(0.155, 0.155, 0.18);
		colText     = Color(0.88, 0.885, 0.9);
		colDim      = Color(0.56, 0.57, 0.63);
		// near-white: selection, drop targets, the loose cable, play on - so
		// orange means audio and nothing else
		colAccent   = Color.grey(0.92);
		// what flows, Okabe-Ito: audio #E69F00, control #56B4E9
		colAudio    = Color(0.902, 0.624, 0.0);
		colControl  = Color(0.337, 0.706, 0.914);
		colKnob     = Color(0.32, 0.32, 0.37);
		colTrack    = Color(0.11, 0.11, 0.13);
		colValue    = Color(0.42, 0.76, 0.95);
		colJackRing = Color(0.63, 0.64, 0.69);
		colJackHole = Color(0.06, 0.06, 0.07);
		colRec      = Color(0.93, 0.26, 0.24);
		cableStyles = [\routed, \curved, \straight];
		paletteNames = [\red, \yellow, \green, \teal, \indigo, \purple, \pink, \white];
		palette = IdentityDictionary[
			\red -> Color(0.93, 0.33, 0.31), \yellow -> Color(0.98, 0.86, 0.3),
			\green -> Color(0.40, 0.80, 0.45), \teal -> Color(0.30, 0.80, 0.78),
			\indigo -> Color(0.45, 0.45, 0.98), \purple -> Color(0.74, 0.50, 0.94),
			\pink -> Color(0.95, 0.45, 0.70), \white -> Color.grey(0.92)
		];
		cableColors = [
			Color(0.93, 0.33, 0.31), Color(0.98, 0.78, 0.25), Color(0.40, 0.80, 0.45),
			Color(0.33, 0.62, 0.95), Color(0.74, 0.50, 0.94), Color(0.98, 0.56, 0.25),
			Color(0.30, 0.80, 0.78), Color(0.95, 0.45, 0.70)
		];
	}

	*new { |bounds| ^super.new.init(bounds) }

	// cables as straight lines - cableStyle \straight - or not: curves
	*straightCables { ^cableStyle == \straight }

	*straightCables_ { |flag| cableStyle = if (flag == true) { \straight } { \curved } }

	init { |bounds|
		views = IdentityDictionary.new;
		commentViews = IdentityDictionary.new;
		zOrder = List.new;
		selection = [];
		dirtyModules = IdentitySet.new;
		dirtyState = IdentitySet.new;
		dirtyMonitor = IdentitySet.new;
		displaysDue = IdentitySet.new;
		inputCache = IdentityDictionary.new;
		liveCache = IdentityDictionary.new;
		liveHistory = IdentityDictionary.new;
		cableCache = [];
		font = Font(Font.defaultSansFace, 11);
		boldFont = Font(Font.defaultSansFace, 12, true);
		hints = NdefRackHintBar();
		this.prBuild(bounds);
		// these can arrive from any thread - they only mark, the frame draws
		ctrl = SimpleController(NdefRack)
			.put(\modules, { dirtyAll = true })
			.put(\module, { |who, what, key| dirtyModules.add(key) })
			.put(\state, { |who, what, key| dirtyState.add(key) })
			.put(\monitor, { |who, what, key| dirtyMonitor.add(key) })
			.put(\position, { |who, what, key| { this.prFollowPosition(key) }.defer })
			.put(\positions, { { this.prFollowPositions }.defer })
			.put(\bends, { dirtyCables = true })
			.put(\comments, { dirtyComments = true })
			.put(\boxes, { dirtyBoxes = true })
			.put(\compact, { |who, what, key| dirtyModules.add(key) })
			.put(\page, { |who, what, key| dirtyModules.add(key) })
			.put(\colors, { dirtyCables = true; dirtyBoxes = true })
			.put(\patches, { { this.prRefreshPatches }.defer });
		// BusRecorder, when it is installed: its takes and sources, for the
		// rec button and the marks on recorded modules
		NdefRack.recorder !? { |rec|
			recCtrl = SimpleController(rec)
				.put(\sources, { recDirty = true })
				.put(\recording, { recDirty = true });
		};
		// the controllers: what turns which control, learn mode, follow - from
		// MIDI's thread as often as not
		ktlCtrl = SimpleController(NdefRackKtl)
			.put(\bindings, { ktlDirty = true })
			.put(\learn, { ktlDirty = true })
			.put(\follow, { ktlDirty = true });
		wasWatching = NdefRack.watching;
		NdefRack.watch;
		// Mapper, when it is installed: its mappings, its learn mode and the
		// control waiting for a controller - watching registered the rack there
		NdefRackMapper.mapper !? { |m|
			mapperCtrl = SimpleController(m)
				.put(\maps, { ktlDirty = true })
				.put(\map, { ktlDirty = true })
				.put(\learn, { ktlDirty = true })
				.put(\learnMode, { NdefRackMapper.prLearnModeChanged; ktlDirty = true });
		};
		this.prRefreshPatches;
		// shown first: the first frame places modules, and before the window
		// is up the rack has no width to place them in
		window.front;
		canvas.focus(true);
		this.prStartUpdater;
		^this
	}

	front { window !? { if (window.isClosed.not) { window.front } } }

	close {
		window !? {
			if (window.isClosed.not) {
				this.prKeepWriting;
				window.close
			}
		}
	}

	isClosed { ^window.isNil or: { window.isClosed } }

	// ---------------------------------------------------------------- selection

	// the selected modules' keys, in the order they were selected
	selection { ^selection.copy }

	isSelected { |key| ^selection.includes(key) }

	select { |keys|
		var old = selection;
		selection = keys.asArray.collect(_.asSymbol).select { |k| NdefRack.modules.at(k).notNil };
		this.prSelectionChanged(old);
	}

	selectAll { this.select(NdefRack.names) }

	deselect { this.select([]) }

	// what a module's menu acts on: the selection when the module is in one,
	// otherwise the module alone
	actionKeys { |key|
		^if (selection.includes(key) and: { selection.size > 1 }) { selection.copy } { [key] }
	}

	// ----------------------------------------------- delete, copy and paste

	deleteModules { |keys|
		var gone;
		keys = keys.asArray;
		if (keys.isEmpty) { ^this };
		// a cable to them may be the one under the mouse: its picture goes now,
		// not a frame later
		this.prHidePreview;
		gone = NdefRack.removeAll(keys).collect(_.name);
		this.select(selection.reject { |k| gone.includes(k) });
		hints.note("deleted % - cmd-z brings % back".format(this.prList(gone),
			if (gone.size == 1) { "it" } { "them" }));
	}

	undoDelete {
		var back;
		if (NdefRack.canUndoRemove.not) {
			hints.note("nothing deleted to bring back");
			^this
		};
		back = NdefRack.undoRemove;
		this.select(back);
		hints.note("brought back %".format(this.prList(back)));
	}

	copyModules { |keys|
		keys = keys.asArray;
		if (keys.isEmpty) { ^this };
		NdefRack.copy(keys);
		hints.note("copied % - cmd-v pastes, with the cables between them".format(this.prList(keys)));
	}

	cutModules { |keys|
		keys = keys.asArray;
		if (keys.isEmpty) { ^this };
		this.prHidePreview;
		NdefRack.cut(keys);
		this.select(selection.reject { |k| keys.includes(k) });
		hints.note("cut % - cmd-v pastes them back".format(this.prList(keys)));
	}

	// at: a point on the rack for the top left of what is pasted
	pasteModules { |at|
		var made;
		if (NdefRack.clipboard.isNil) {
			hints.note("nothing copied yet - select modules and cmd-c");
			^this
		};
		made = NdefRack.paste(at);
		this.select(made);
		hints.note("pasted % - not playing until you play it".format(this.prList(made)));
	}

	duplicateModules { |keys|
		var made;
		keys = keys.asArray;
		if (keys.isEmpty) { ^this };
		made = NdefRack.duplicate(keys);
		this.select(made);
		hints.note("duplicated as %".format(this.prList(made)));
	}

	// the record menu: every audio module among keys made a source of
	// BusRecorder's takes, or not any more
	setRecorded { |keys, flag = true|
		keys = keys.asArray.select { |k| NdefRack.canRecord(k) };
		if (keys.isEmpty) { ^this };
		keys.do { |k| NdefRack.setRecorded(k, flag) };
		hints.note(if (flag) {
			"% recorded in BusRecorder's next take - rec to start it".format(this.prList(keys))
		} {
			"% not recorded any more".format(this.prList(keys))
		});
	}

	// compact or not: only the controls away from their defaults, or all
	setCompact { |keys, flag = true|
		keys = keys.asArray;
		keys.do { |k| NdefRack.setCompact(k, flag) };
		hints.note(if (flag) {
			"% compact - only what is not at its default; the button again shows every control".format(this.prList(keys))
		} {
			"% shows every control".format(this.prList(keys))
		});
	}

	// the voices menu: every module among keys that can have voices
	setVoices { |keys, n|
		keys = keys.asArray.select { |k| NdefRack.canVoice(k) };
		if (keys.isEmpty) { ^this };
		keys.do { |k| NdefRack.setVoices(k, n) };
		hints.note("% - % voice%".format(this.prList(keys), n, if (n == 1) { "" } { "s" }));
	}

	// ------------------------------------------------------- read by the views

	// the value a cable from this proxy carries into channel ch, right now -
	// nil for an audio rate source, which shared memory cannot read
	liveValue { |proxy, ch = 0|
		^this.liveValues(proxy) !? { |vals| vals[ch] }
	}

	liveValues { |proxy|
		^NdefRack.prModuleKey(proxy) !? { |key| liveCache.at(key) }
	}

	// The colour of the cable into an input: what flows in it - audio orange,
	// control blue - from the rate of the module it comes from. A plugged
	// port fills with it, so audio arriving at a control shows at a glance.
	cableColor { |to, param|
		var m = NdefRack.modules.at(to);
		var src = m !? { m.source(param) };
		^this.rateColor(src !? { NdefRack.prModuleKey(src) })
	}

	// the colour of what a module's output carries
	rateColor { |key|
		var m = key !? { NdefRack.modules.at(key) };
		^case
		{ m.isNil } { colDim }
		{ m.isAudio } { colAudio }
		{ m.isControl } { colControl }
		{ colDim }
	}

	// the module keys, the one on top first: the most recently made, rebuilt
	// or dragged - the order a drop is hit-tested in
	zOrder { ^zOrder.copy }

	// what a loose cable is doing: \out (from an output), \in (from an empty
	// input), \carry (a plugged one picked up) - nil while none is loose
	cableMode { ^cableDrag !? { cableDrag[\mode] } }

	// the loose cable a click left armed - an Event with its \mode, \from,
	// \to, \param and \point - or nil: a press and release in place on an
	// output or an input leaves the cable on the mouse with no button held,
	// and the next click lands it
	armedCable { ^cableDrag !? { if (cableDrag[\armed] == true) { cableDrag.copy } } }

	// the colour of what the loose cable would carry onto its target: the
	// rate of the module it comes from - for a cable from an input, of the
	// module under it
	dragColor {
		^cableDrag !? {
			if (cableDrag[\mode] == \in) {
				this.rateColor(dragTarget !? { dragTarget[0] })
			} {
				this.rateColor(cableDrag[\from])
			}
		}
	}

	// the lowest and highest value a control rate module sent on channel ch
	// over the last historyLength frames - [lo, hi] - or nil while there is
	// no history: an audio source, no local server
	liveRange { |proxy, ch = 0|
		var hist = NdefRack.prModuleKey(proxy) !? { |key| liveHistory.at(key) };
		var vals;
		if (hist.isNil or: { hist.isEmpty }) { ^nil };
		vals = hist.collect { |frame| frame[ch] }.reject(_.isNil);
		if (vals.isEmpty) { ^nil };
		^[vals.minItem, vals.maxItem]
	}

	// ------------------------------------------------------------------ build

	prBuild { |bounds|
		bounds = bounds ?? { Rect(60, 60, 1200, 800) };
		window = Window("ndef rack - " ++ NdefRack.server.name, bounds);
		if (GUI.id == \qt) { window.view.palette = QPalette.dark };
		window.view.background = colBg;

		// a window made bigger - maximised on a wide screen - makes the canvas
		// bigger too, or the rack past its old edge takes no clicks and clips
		// the cables
		scroll = ScrollView().hasBorder_(false).background_(colBg)
			.onResize_({ this.prResizeCanvas });
		// the grid is an image Qt tiles: drawn dot by dot from the language it
		// took 10-25 ms on a wide screen, every time a dragged module moved
		canvas = UserView(scroll, Rect(0, 0, bounds.width, bounds.height))
			.background_(colBg)
			.setBackgroundImage(this.prGridImage, 5)
			.canFocus_(true)
			.mouseDownAction_({ |uv, x, y, mod, button, clicks| this.prCanvasDown((x @ y) / zoom, mod, button, clicks) })
			.mouseMoveAction_({ |uv, x, y, mod| this.prCanvasMove((x @ y) / zoom, mod) })
			.mouseUpAction_({ |uv, x, y, mod| this.prCanvasUp((x @ y) / zoom, mod) })
			// cmd (or control) and the wheel: the zoom, about the mouse
			.mouseWheelAction_({ |uv, x, y, mod, dx, dy|
				if (mod.isCmd or: { mod.isCtrl }) {
					if (dy != 0) { this.zoomStep(dy.sign, (x @ y) / zoom) };
					true
				}
			})
			.keyDownAction_({ |v, char, mod, unicode, keycode, key| this.prKeyDown(mod, key) });
		// first child of the canvas, so every view made after it is above it
		// the network boxes first of all: behind the cables and the modules
		this.prMakeBoxLayer;
		this.prMakeCableLayer;
		this.prMakeOverlay;
		this.prMakePreview;
		hints.attach(canvas, { |v, x, y|
			views.do { |mv| mv.prHoverOut };
			this.prArmedHint ?? { this.cableHintAt((x @ y) / zoom) } ?? { this.prBoxHintAt((x @ y) / zoom) } ?"the rack - right click to add a module here, drag to select, shift-drag to add to the selection, cmd-v pastes"
		});

		statusText = StaticText().font_(font).stringColor_(colDim);
		hints.attach(statusText, "how many modules and cables, and whether the server is there to play them");
		// the module library down the left: NdefRackBrowser, folded away with
		// the toolbar's modules button or cmd-b
		browser = NdefRackBrowser(this);
		window.layout = VLayout(
			this.prToolbar,
			[HLayout(browser.view, [scroll, stretch: 1]).margins_(0).spacing_(6), stretch: 1],
			HLayout([statusText, stretch: 1], hints.toggle.canFocus_(false)).margins_([2, 0, 2, 0]),
			hints.view
		).margins_(6).spacing_(4);
		window.view.keyDownAction = { |v, char, mod, unicode, keycode, key| this.prKeyDown(mod, key) };
		// moved, perhaps to a screen of another pixel ratio: the frame remakes
		// the pictures once the window rests (prRemakePictures)
		window.view.onMove = { movedAt = Main.elapsedTime };
		hints.watch(window);

		window.onClose = {
			ctrl.remove;
			browser !? (_.remove);
			recCtrl !? (_.remove);
			ktlCtrl.remove;
			mapperCtrl !? (_.remove);
			// learn mode the window turned on goes with it
			NdefRackMapper.windowClosed;
			updater !? (_.stop);
			views.do(_.remove);
			this.prEndBoxName(true);
			commentViews.do(_.remove);
			probe !? { probe.free; probe = nil };
			if (wasWatching.not) { NdefRack.unwatch };
			NdefRack.prGuiClosed;
		};
	}

	prToolbar {
		var opacity;
		patchMenu = PopUpMenu().fixedWidth_(124).font_(font).canFocus_(false)
			.action_({ |v|
				var i = v.value ? 0;
				var path = if (i > 0) { patchPaths[i - 1] };
				v.value = 0;
				path !? { NdefRack.loadPatch(path) };
			});
		hints.attach(patchMenu, "load a saved patch: its modules, values, cables and places, newest first");
		opacity = Slider().orientation_(\horizontal).fixedWidth_(70).fixedHeight_(16)
			.canFocus_(false)
			.value_(cableAlpha)
			.action_({ |v|
				cableAlpha = v.value.max(0.1);
				this.prRefreshCables;
			});
		hints.attach(opacity, "how strongly the cables behind the modules are drawn");
		styleMenu = PopUpMenu().fixedWidth_(96).font_(font).canFocus_(false)
			.items_(["flow chart", "curves", "straight"])
			.value_(cableStyles.indexOf(cableStyle) ? 0)
			.action_({ |v|
				cableStyle = cableStyles[v.value ? 0];
				this.prRefreshCables;
			});
		browserButton = Button().fixedWidth_(68).font_(font).canFocus_(false)
			.states_([["modules", colText, colPanel], ["modules", Color.black, colAccent]])
			.value_(NdefRackBrowser.shown.binaryValue)
			.action_({ |b| this.showBrowser(b.value == 1) });
		hints.attach(browserButton, { "the panel of every module in the library - searched, sorted, double click or drag one onto the rack - cmd-b" });
		hints.attach(styleMenu, "how cables are drawn: a flow chart - straight runs at right angles round the modules, click a cable and drag where it bends - curves, or straight lines");
		NdefRack.recorder !? { |rec|
			recButton = Button().fixedWidth_(52).font_(font).canFocus_(false)
				.states_([["rec", colText, colPanel], ["stop", Color.white, colRec]])
				.action_({ |b|
					// the button follows the recorder, not the click
					if (rec.isRecording) { rec.stop } { rec.record };
					b.value = rec.isRecording.binaryValue;
				});
			hints.attach(recButton, {
				if (rec.isRecording) { "stop the take" } {
					"record a take with BusRecorder: every armed source - right click an audio module, record, to make it one"
				}
			});
		};
		// groups, with a 12 px spacer between them: [add, arrange] | [the patch
		// as code, saved, loaded] | [learn] | [rec, recorder] | stretch |
		// [cables, how they are drawn]
		^HLayout(*(this.prGroups([
			[
				StaticText().string_("ndef rack").font_(boldFont).stringColor_(colAccent)
					.fixedWidth_(80),
				browserButton,
				this.prButton("+ module", 76, { this.prAddMenu.front },
					"add a module from the library - or right click on the empty rack, where you want it"),
				this.prButton("arrange", 62, {
					if (selection.size > 1) { this.arrangeModules(selection) } { this.prArrange }
				}, "lay the modules out left to right, the way the signal flows - with modules selected, only them, by the layout rules: a row for the main path, a row under it for each other part (a)"),
				this.prButton("+ comment", 80, { this.addComment },
					"a box of text in the middle of what is seen - or right click on the empty rack, where you want it")
			],
			[
				this.prButton("-", 24, { this.zoomStep(-1) }, "zoom out - cmd-minus, or cmd and the wheel"),
				zoomButton = this.prButton("100%", 50, { this.zoom_(1) }, "the zoom - click for 100%, cmd-0"),
				this.prButton("+", 24, { this.zoomStep(1) }, "zoom in - cmd-plus, or cmd and the wheel"),
				this.prButton("fit", 36, { this.fitAll }, "everything on the rack in view - f"),
				this.prButton("centre", 54, { this.centre }, "back to the middle of the patch, at this zoom - h")
			],
			[
				this.prButton("patch as code", 96, { NdefRack.patchDocument },
					"the whole rack as Ndef(...).set(...) lines, in a new document"),
				this.prButton("save patch", 78, {
					Dialog.savePanel({ |p| NdefRack.savePatch(p) }, path: NdefRack.ensurePatchDir);
				}, "save the rack to a file in patches/ - modules, values, cables, places"),
				patchMenu
			],
			// the controllers: learn - Mapper's learn mode when it is installed,
			// else NdefRackKtl's - and Mapper's window
			({ |g| if (g.isEmpty) { nil } { g } }).value((if (NdefRackMapper.canLearn) {
				learnButton = Button().fixedWidth_(58).font_(font).canFocus_(false)
					.states_([["learn", colText, colPanel], ["learning", Color.black, colAccent]])
					.action_({ |b| NdefRackMapper.learning = b.value == 1 });
				hints.attach(learnButton, {
					if (NdefRackMapper.learning) {
						"stop learning - escape does it too"
					} {
						"learn a controller: click a control, button or gate, then move something on any MKtl - backspace forgets what turns the clicked one"
							++ (if (NdefRackMapper.usesMapper) { " (Mapper's learn mode, in every window it maps)" } { "" })
					}
				});
				[learnButton]
			} { [] }) ++ (if (NdefRackMapper.usesMapper) {
				[this.prButton("mapper", 64, { NdefRackMapper.gui },
					"Mapper's window: the controllers, every mapping - the rack's controls are under ndef rack - their ranges and curves")]
			} { [] })),
			NdefRack.recorder !? { |rec|
				[recButton, this.prButton("recorder", 70, { rec.gui },
					"BusRecorder's window: its sources, how a take is split into files, where they go")]
			}
		]) ++ [
			// a stretch
			nil,
			StaticText().string_("cables").font_(font).stringColor_(colDim),
			opacity,
			styleMenu
		])).spacing_(5)
	}

	// the groups present, one list, a spacer between each two
	prGroups { |groups|
		var out = [];
		groups.select(_.notNil).do { |g, i|
			if (i > 0) { out = out.add(View().fixedWidth_(12)) };
			out = out ++ g;
		};
		^out
	}

	prButton { |str, width, func, hint|
		var b = Button().fixedWidth_(width).font_(font).canFocus_(false)
			.states_([[str, colText, colPanel]])
			.action_({ func.value });
		hints.attach(b, hint ? "");
		^b
	}

	// The fixed cables are a view of their own behind every module: the
	// canvas's first child, never brought forward, so each module view - made
	// later, or brought forward to be dragged - sits above it (measured over
	// the canvas's tiled grid, 2026-11-20). A cable passes behind any module it
	// crosses and never covers a label, a value or a port.
	// The network boxes are drawn on a view of their own, the canvas's very
	// first child, never brought forward: behind the cables and every module.
	// It takes no mouse - the canvas's own mouse finds a box's title.
	prMakeBoxLayer {
		boxLayer = UserView(canvas, Rect(0, 0, canvas.bounds.width, canvas.bounds.height))
			.acceptsMouse_(false)
			.drawFunc_({ Pen.scale(zoom, zoom); this.prDrawBoxes });
	}

	prMakeCableLayer {
		cableLayer = UserView(canvas, Rect(0, 0, canvas.bounds.width, canvas.bounds.height))
			.acceptsMouse_(false)
			.drawFunc_({ Pen.scale(zoom, zoom); this.prDrawCables });
	}

	// The overlay, above every module, lets the mouse through to whatever is
	// under it. It draws only the loose cable, the one under the mouse and the
	// cables of the selected modules - to trace them end to end - and the
	// band. A module view made later, or brought forward to be dragged, sits
	// on top of it - so the overlay is brought forward again after.
	prMakeOverlay {
		overlay = UserView(canvas, Rect(0, 0, canvas.bounds.width, canvas.bounds.height))
			.acceptsMouse_(false)
			.drawFunc_({ Pen.scale(zoom, zoom); this.prDrawOverlay });
	}

	// both layers of cables drawn again: a module moved, the cables changed -
	// and the flow chart's ways found again
	prRefreshCables {
		routeCache = nil;
		cableLayer.refresh;
		overlay.refresh;
	}

	// The overlay above the modules, and the preview above the cables - only
	// while it is open: View:front shows a view as well as raising it, and a
	// closed preview brought back that way is its last picture, left behind.
	prOverlayToFront {
		overlay.front;
		if (previewShown) { previewView !? { previewView.front } };
	}

	// ----------------------------------------------------------- the canvas

	prCanvasDown { |point, mod, button, clicks = 1|
		var cable;
		canvas.focus(true);
		// an armed cable: this click lands it - on the empty rack, lets it go
		if (this.armedCable.notNil) {
			if (button == 1 or: { mod.isCtrl }) { this.prCableCancel } { this.prArmedClick(point) };
			^true
		};
		if (button == 1 or: { mod.isCtrl }) {
			// a network box's title: the box's own menu
			this.prBoxTitleAt(point) !? { |id| this.prBoxMenu(id).front; ^true };
			this.prCanvasMenu(point).front;
			^true
		};
		// on a cable: it is selected, and a drag moves where it bends; a
		// double click gives the bend back to the window
		cable = this.prCableAt(point);
		if (cable.notNil) {
			this.deselect;
			this.selectComment(nil);
			this.selectCable(cable);
			if ((clicks ? 1) >= 2) {
				this.unbendCable(cable)
			} {
				this.prBendBegin(cable, point)
			};
			^true
		};
		this.selectCable(nil);
		this.selectComment(nil);
		// a network box's title: selected, and a drag moves it and what is in
		// it; its corner resizes it; a double click on the title renames it
		this.prBoxCornerAt(point) !? { |id|
			this.deselect;
			this.selectBox(id);
			this.prBoxBegin(id, point, \size);
			^true
		};
		this.prBoxTitleAt(point) !? { |id|
			this.deselect;
			this.selectBox(id);
			if ((clicks ? 1) >= 2) { this.renameBoxField(id) } { this.prBoxBegin(id, point, \move) };
			^true
		};
		this.selectBox(nil);
		// a drag on the empty rack draws a band; shift keeps what was selected
		band = (start: point, point: point, keep: if (mod.isShift) { selection.copy } { [] });
		if (mod.isShift.not) { this.deselect };
		^true
	}

	prCanvasMove { |point, mod|
		var rect, hit;
		bendDrag !? { ^this.prBendMove(point, mod) };
		boxDrag !? { ^this.prBoxMove(point) };
		band ?? { ^true };
		band[\point] = point;
		rect = Rect.fromPoints(band[\start], point);
		hit = views.keys.select { |k| views.at(k).bounds.intersects(rect) }.asArray;
		this.select(band[\keep] ++ hit.reject { |k| band[\keep].includes(k) });
		overlay.refresh;
		^true
	}

	prCanvasUp { |point, mod|
		bendDrag !? { this.prBendEnd };
		boxDrag !? { this.prBoxEnd };
		band = nil;
		overlay.refresh;
		^true
	}

	// ------------------------------------------------- a cable, selected

	// The cable clicked on the empty rack - an Event as NdefRack.cables has
	// it - or nil. It is drawn above the modules, with a handle on each run
	// a drag can move; backspace unplugs it.
	selectCable { |cable|
		var c = cable !? { (from: cable[\from], to: cable[\to], param: cable[\param], kind: \map) };
		if (this.prSameCable(c, selectedCable).not and: { (c.isNil and: { selectedCable.isNil }).not }) {
			selectedCable = c;
			overlay !? { overlay.refresh };
			c !? { hints.string = this.prSelectedHint(c) };
		};
	}

	// ------------------------------------------------------ network boxes

	// A network box round the selected modules - or, with none, an empty one
	// in the middle of what is seen.
	boxSelection {
		var rects = selection.collect { |k| views.at(k) !? (_.bounds) }.reject(_.isNil);
		var r;
		if (rects.isEmpty) { ^this.addBox };
		r = rects.inject(rects.first, { |a, b| a.union(b) });
		r = Rect(r.left - 20, r.top - 20 - this.boxTitleH, r.width + 40, r.height + 40 + this.boxTitleH);
		^this.addBox(r.origin, r.extent)
	}

	addBox { |point, size, name, color = \teal|
		var id;
		point = point ?? { this.prSeen.center - Point(200, 150) };
		point = Point(point.x.round(10).max(0), point.y.round(10).max(0));
		size = size ?? { Point(400, 300) };
		id = NdefRack.addBox(name, Rect(point.x, point.y, size.x.round(10), size.y.round(10)), color);
		if (name.isNil) { NdefRack.renameBox(id, "network " ++ id.asString.drop(3)) };
		this.selectBox(id);
		hints.note("a network box - drag its title to move it and what is in it, double click the title to name it");
		^id
	}

	deleteBox { |id|
		id ?? { ^this };
		if (selectedBox == id) { selectedBox = nil };
		NdefRack.removeBox(id);
		hints.note("network box deleted - what was in it stays");
	}

	selectBox { |id|
		if (selectedBox != id) {
			selectedBox = id;
			boxLayer !? { boxLayer.refresh };
		}
	}

	boxTitleH { ^22 }

	// the box whose title strip is under point, the last made on top
	prBoxTitleAt { |point|
		^this.prBoxIds.reverse.detect { |id|
			var r = this.prBoxRect(id);
			Rect(r.left, r.top, r.width, this.boxTitleH).containsPoint(point)
		}
	}

	prBoxCornerAt { |point|
		^this.prBoxIds.reverse.detect { |id|
			var r = this.prBoxRect(id);
			(point.x <= r.right) and: { point.x > (r.right - 14) } and: { point.y <= r.bottom } and: { point.y > (r.bottom - 14) }
		}
	}

	prBoxIds { ^NdefRack.boxIds }

	// where a box is drawn: being dragged, or where NdefRack has it
	prBoxRect { |id|
		^(boxDrag !? { boxDrag[\rects].at(id) }) ?? { NdefRack.boxRect(id) }
	}

	prBoxHintAt { |point|
		this.prBoxCornerAt(point) !? { ^"drag to make the network box bigger or smaller" };
		^this.prBoxTitleAt(point) !? { |id|
			"network box % - drag it to move it and everything in it, double click to name it, right click for its colour".format(NdefRack.boxes.at(id)[\name])
		}
	}

	prBoxMenu { |id|
		^Menu(
			MenuAction("name it", { this.renameBoxField(id) }),
			this.colorMenu(NdefRack.boxes.at(id) !? { |b| b[\color] }, { |col| NdefRack.colorBox(id, col ? \teal) }).title_("colour"),
			MenuAction("select what is in it", { this.select(this.prInBox(id)) }),
			MenuAction.separator,
			MenuAction("delete the box - not what is in it", { this.deleteBox(id) })
		)
	}

	// the modules and comments whose middle is in a box, and the boxes wholly in it
	prInBox { |id|
		var r = NdefRack.boxRect(id);
		r ?? { ^[] };
		^views.keys.asArray.select { |k| r.containsPoint(views.at(k).bounds.center) }
	}

	prBoxBegin { |id, point, mode|
		var r = NdefRack.boxRect(id);
		var inner = this.prBoxIds.select { |b| b != id and: { r.contains(NdefRack.boxRect(b)) } };
		var mods = if (mode == \move) { this.prInBox(id) } { [] };
		var notes = if (mode == \move) {
			commentViews.keys.asArray.select { |k| r.containsPoint(commentViews.at(k).bounds.center) }
		} { [] };
		boxDrag = (id: id, mode: mode, start: point, moved: false, rect: r,
			rects: IdentityDictionary.new.put(id, r),
			inner: if (mode == \move) { inner } { [] },
			mods: mods, notes: notes,
			origins: IdentityDictionary.new, noteOrigins: IdentityDictionary.new);
		boxDrag[\inner].do { |b| boxDrag[\rects].put(b, NdefRack.boxRect(b)) };
		mods.do { |k| boxDrag[\origins].put(k, views.at(k).origin) };
		notes.do { |k| boxDrag[\noteOrigins].put(k, commentViews.at(k).origin) };
	}

	prBoxMove { |point|
		var d = boxDrag, delta = point - d[\start], r = d[\rect];
		delta = Point(delta.x.round(10), delta.y.round(10));
		if (d[\mode] == \move) {
			delta = Point(delta.x.max(r.left.neg), delta.y.max(r.top.neg));
			d[\origins].do { |o| delta = Point(delta.x.max(o.x.neg), delta.y.max(o.y.neg)) };
			d[\rects].put(d[\id], r.moveBy(delta.x, delta.y));
			d[\inner].do { |b| d[\rects].put(b, NdefRack.boxRect(b).moveBy(delta.x, delta.y)) };
			d[\origins].keysValuesDo { |k, o| views.at(k) !? { |v| v.moveTo(o + delta) } };
			d[\noteOrigins].keysValuesDo { |k, o| commentViews.at(k) !? { |v| v.moveTo(o + delta) } };
		} {
			d[\rects].put(d[\id], Rect(r.left, r.top, (r.width + delta.x).max(120), (r.height + delta.y).max(60)));
		};
		if (delta != Point(0, 0)) { d[\moved] = true };
		d[\delta] = delta;
		boxLayer.refresh;
		this.prRefreshCables;
		^true
	}

	prBoxEnd {
		var d = boxDrag;
		boxDrag = nil;
		if (d[\moved]) {
			d[\origins].keysDo { |k| views.at(k) !? { |v| NdefRack.positions.put(k, v.origin) } };
			d[\noteOrigins].keysDo { |k| commentViews.at(k) !? { |v| NdefRack.placeComment(k, v.bounds) } };
			d[\rects].keysValuesDo { |b, r| NdefRack.placeBox(b, r) };
			this.prMoveBends(d[\mods], d[\delta]);
			this.prResizeCanvas;
		};
		boxLayer.refresh;
		this.prRefreshCables;
	}

	// a field over the box's title to name it: return or a click elsewhere
	// keeps the name, escape leaves it as it was
	renameBoxField { |id|
		var r = NdefRack.boxRect(id), b = NdefRack.boxes.at(id);
		r ?? { ^this };
		this.prEndBoxName(true);
		boxNaming = id;
		boxDraft = b[\name];
		boxField = TextField(canvas, Rect(r.left * zoom + 4, r.top * zoom + 1, (r.width - 8).min(260) * zoom, (this.boxTitleH - 2) * zoom))
			.font_(Font(boldFont.name, boldFont.size * zoom, true))
			.string_(b[\name])
			.action_({ |f| if (f === boxField) { this.prEndBoxName(true) } })
			.focusLostAction_({ |f| if (f === boxField) { this.prEndBoxName(true) } })
			.keyDownAction_({ |f, char, mod, unicode, keycode, key|
				if (key == 16r01000000 and: { f === boxField }) { this.prEndBoxName(false); true }
			});
		boxField.focus(true);
	}

	prEndBoxName { |keep|
		var field = boxField;
		field ?? { ^this };
		this.prKeepWriting;
		boxField = nil;
		if (keep) { NdefRack.renameBox(boxNaming, boxDraft) };
		{ if (field.isClosed.not) { field.remove } }.defer(0.01);
	}

	// Every frame: what is being written in a comment or a box's name. The
	// window closing takes the fields with it, text and all, before it says so.
	prKeepWriting {
		commentViews.do(_.prKeepDraft);
		boxField !? { if (boxField.isClosed.not) { boxDraft = boxField.string } };
	}

	// a module to look at again in the next frame
	prStateChanged { |key| dirtyState.add(key) }

	// Each box: its colour faint over the rack, a stronger title strip with
	// its name, a line round it - white when selected - and a grip in its
	// corner. Drawn in the order made, so a box made later is over an older one.
	prDrawBoxes {
		this.prBoxIds.do { |id|
			var b = NdefRack.boxes.at(id), r = this.prBoxRect(id);
			var col = this.paletteColor(b[\color]) ? colDim;
			var sel = selectedBox == id;
			Pen.fillColor = col.copy.alpha_(0.07);
			Pen.addRoundedRect(r, 6, 6);
			Pen.fill;
			Pen.use {
				Pen.addRoundedRect(r, 6, 6);
				Pen.clip;
				Pen.fillColor = col.copy.alpha_(0.28);
				Pen.fillRect(Rect(r.left, r.top, r.width, this.boxTitleH));
			};
			Pen.strokeColor = if (sel) { colAccent } { col.copy.alpha_(0.55) };
			Pen.width = if (sel) { 2 } { 1 };
			Pen.addRoundedRect(r.insetBy(0.5, 0.5), 6, 6);
			Pen.stroke;
			Pen.stringAtPoint(b[\name], Point(r.left + 8, r.top + 4), boldFont, colText);
			Pen.strokeColor = col.copy.alpha_(0.7);
			Pen.width = 1;
			[5, 10].do { |dd| Pen.line(Point(r.right - 3, r.bottom - 3 - dd), Point(r.right - 3 - dd, r.bottom - 3)) };
			Pen.stroke;
		};
	}

	// ---------------------------------------------------------- comments

	// A comment at point - the middle of what is seen when nil - with a field
	// open to write in it.
	addComment { |point, text = ""|
		var id;
		point = point ?? { this.prSeen.center - Point(110, 40) };
		point = Point(point.x.round(10).max(0), point.y.round(10).max(0));
		id = NdefRack.addComment(text, point);
		this.prSyncComments;
		this.selectComment(id);
		if (text.isEmpty) { commentViews.at(id) !? (_.edit) };
		^id
	}

	deleteComment { |id|
		id ?? { ^this };
		if (selectedComment == id) { selectedComment = nil };
		NdefRack.removeComment(id);
		this.prSyncComments;
		hints.note("comment deleted");
	}

	selectComment { |id|
		var old = selectedComment;
		if (old == id) { ^this };
		selectedComment = id;
		[old, id].do { |k| k !? { commentViews.at(k) !? (_.refresh) } };
	}

	// a click on a comment: it alone is selected
	prCommentClicked { |id|
		canvas.focus(true);
		this.selectBox(nil);
		this.deselect;
		this.selectCable(nil);
		this.selectComment(id);
	}

	// the comment views as NdefRack has the comments
	prSyncComments {
		var have = NdefRack.comments;
		dirtyComments = false;
		commentViews.keys.asArray.do { |id|
			if (have.at(id).isNil) { commentViews.removeAt(id).remove }
		};
		have.keysDo { |id|
			var v = commentViews.at(id);
			if (v.isNil) {
				v = NdefRackCommentView(this, id);
				commentViews.put(id, v);
				this.prOverlayToFront;
			} {
				v.follow
			}
		};
		if (selectedComment.notNil and: { have.at(selectedComment).isNil }) { selectedComment = nil };
		this.prResizeCanvas;
		this.prRefreshCables;
	}

	// a cable's bend given back: the window finds its way again
	unbendCable { |c|
		c ?? { ^this };
		NdefRack.bend(c[\from], c[\to], c[\param], nil);
		hints.note("% → % %: the window finds its way again".format(c[\from], c[\to], c[\param]));
	}

	prSelectedHint { |c|
		^"% → % % - %backspace unplugs it, a click on the empty rack lets it go".format(
			c[\from], c[\to], c[\param],
			if (cableStyle == \routed) { "drag it to move where it bends, a double click lets the window route it again, " } { "" })
	}

	// A press on a cable drawn as a flow chart: the run under the mouse is the
	// one the drag moves - a vertical run sideways, the lane up or down.
	prBendBegin { |c, point|
		var pts;
		if (cableStyle != \routed) { ^this };
		pts = this.prPathOf(c);
		if (pts.isNil or: { NdefRackRouter.bendOf(pts).isNil }) { ^this };
		bendDrag = (cable: c, start: point, points: pts,
			run: NdefRackRouter.nearestRun(pts, point)[0], moved: false);
	}

	// on the grid the modules snap to; shift: free
	prBendMove { |point, mod|
		var d = bendDrag, delta, bend;
		delta = point - d[\start];
		if (mod.notNil and: { mod.isShift.not }) { delta = Point(delta.x.round(10), delta.y.round(10)) };
		if (delta.rho >= 1) { d[\moved] = true };
		bend = NdefRackRouter.dragBend(d[\points], d[\run], delta);
		bend !? {
			if (bend != d[\bend]) {
				d[\bend] = bend;
				this.prRefreshCables;
			}
		};
		^true
	}

	prBendEnd {
		var d = bendDrag;
		bendDrag = nil;
		if (d[\moved] and: { d[\bend].notNil }) {
			NdefRack.bend(d[\cable][\from], d[\cable][\to], d[\cable][\param], d[\bend]);
		};
		this.prRefreshCables;
	}

	prCanvasMenu { |point| ^Menu(*this.prCanvasActions(point)) }

	// The empty rack's menu: over a cable, unplugging it first - then paste,
	// delete, undo and the library.
	prCanvasActions { |point|
		var acts = [], top = [];
		this.prCableAt(point) !? { |c|
			top = [MenuAction("unplug % → % %".format(c[\from], c[\to], c[\param]),
				{ this.prUnplug(c) })];
			if (NdefRack.bendOf(c[\from], c[\to], c[\param]).notNil) {
				top = top.add(MenuAction("let the window find its way", { this.unbendCable(c) }))
			};
			top = top.add(this.colorMenu(NdefRack.cableColorOf(c[\from], c[\to], c[\param]), { |col|
				NdefRack.setCableColor(c[\from], c[\to], c[\param], col)
			}).title_("colour"));
			top = top.add(MenuAction.separator);
		};
		acts = acts.add(MenuAction("comment here", { this.addComment(point) }));
		if (selection.notEmpty) {
			acts = acts.add(MenuAction("network box round the selection   cmd-g", { this.boxSelection }));
		} {
			acts = acts.add(MenuAction("network box here", { this.addBox(point) }));
		};
		if (NdefRack.clipboard.notNil) {
			acts = acts.add(MenuAction("paste here   cmd-v", { this.pasteModules(point) }));
		};
		if (selection.notEmpty) {
			acts = acts.add(MenuAction("delete the selection   backspace", { this.deleteModules(selection) }));
		};
		if (NdefRack.canUndoRemove) {
			acts = acts.add(MenuAction("bring the last delete back   cmd-z", { this.undoDelete }));
		};
		if (acts.notEmpty) { acts = acts.add(MenuAction.separator) };
		^top ++ acts ++ this.prAddActions(point)
	}

	// a cable unplugged from the rack's menu: the input gets its own value back
	prUnplug { |c|
		if (this.prSameCable(c, selectedCable)) { this.selectCable(nil) };
		NdefRack.disconnect(c[\to], c[\param]);
		dirtyCables = true;
		hints.note("unplugged % → % % - it has its own value back".format(c[\from], c[\to], c[\param]));
	}

	// keys answer true when they did something here, nil to pass on
	prKeyDown { |mod, key|
		var cmd = mod.isCmd or: { mod.isCtrl };
		var armed = NdefRackMapper.armed;
		case
		// a loose cable: escape puts it back - a picked up one stays where it
		// was, since nothing is disconnected before the drop
		{ cableDrag.notNil and: { key == 16r01000000 } } { this.prCableCancel }
		// learning: escape lets go of the armed control, then of learn mode;
		// backspace forgets what turns the armed one
		{ NdefRackMapper.learning and: { key == 16r01000000 } } {
			if (armed.notNil) { NdefRackMapper.cancelLearn } { NdefRackMapper.learning = false }
		}
		{ armed.notNil and: { key == 16r01000003 or: { key == 16r01000007 } } } {
			NdefRackMapper.forget(armed[0], armed[1]);
			NdefRackMapper.cancelLearn;
			hints.note("% %: no controller turns it now".format(armed[0], armed[1]));
		}
		// learning, backspace never deletes modules: Mapper may be waiting on
		// a control outside the rack
		{ NdefRackMapper.learning and: { key == 16r01000003 or: { key == 16r01000007 } } } { nil }
		{ cmd and: { key == 16r47 } } { this.boxSelection }
		// the module library: shown or folded away; its search
		{ cmd and: { key == 16r42 } } { this.showBrowser(NdefRackBrowser.shown.not) }
		{ cmd and: { key == 16r46 } } { this.showBrowser(true); browser.field.focus(true) }
		// a selected network box: backspace deletes it - not what is in it
		{ selectedBox.notNil and: { key == 16r01000003 or: { key == 16r01000007 } } } {
			this.deleteBox(selectedBox)
		}
		{ selectedBox.notNil and: { key == 16r01000000 } } { this.selectBox(nil) }
		// a selected comment: backspace deletes it, escape lets it go
		{ selectedComment.notNil and: { key == 16r01000003 or: { key == 16r01000007 } } } {
			this.deleteComment(selectedComment)
		}
		{ selectedComment.notNil and: { key == 16r01000000 } } { this.selectComment(nil) }
		// the view: zoom in, out, 100%; fit everything; back to the middle
		{ cmd and: { key == 16r3D or: { key == 16r2B } } } { this.zoomStep(1) }
		{ cmd and: { key == 16r2D } } { this.zoomStep(-1) }
		{ cmd and: { key == 16r30 } } { this.zoom_(1) }
		{ cmd.not and: { mod.isAlt.not } and: { key == 16r46 } } { this.fitAll }
		{ cmd.not and: { mod.isAlt.not } and: { key == 16r48 } } { this.centre }
		{ cmd.not and: { mod.isAlt.not } and: { key == 16r41 } } { this.arrangeModules(selection) }
		// a selected cable: backspace unplugs it, escape lets it go
		{ selectedCable.notNil and: { key == 16r01000003 or: { key == 16r01000007 } } } {
			this.prUnplug(selectedCable)
		}
		{ selectedCable.notNil and: { key == 16r01000000 } } { this.selectCable(nil) }
		{ key == 16r01000003 or: { key == 16r01000007 } } { this.deleteModules(selection) }
		{ key == 16r01000000 } { this.deselect }
		{ cmd and: { key == 16r43 } } { this.copyModules(selection) }
		{ cmd and: { key == 16r58 } } { this.cutModules(selection) }
		{ cmd and: { key == 16r56 } } {
			// on the grid the modules are dragged on
			this.pasteModules(this.prCursorPoint !? { |p| Point(p.x.round(10), p.y.round(10)) })
		}
		{ cmd and: { key == 16r44 } } { this.duplicateModules(selection) }
		{ cmd and: { key == 16r41 } } { this.selectAll }
		{ cmd and: { key == 16r5A } } { this.undoDelete }
		{ ^nil };
		^true
	}

	// the mouse on the rack, when it is over the visible part of it
	prCursorPoint {
		var p = QtGUI.cursorPosition - canvas.mapToGlobal(Point(0, 0));
		var seen = Rect(scroll.visibleOrigin.x, scroll.visibleOrigin.y,
			scroll.bounds.width, scroll.bounds.height);
		^if (seen.containsPoint(p)) { p / zoom }
	}

	// what is seen of the rack, in the rack's coordinates
	prSeen {
		var o = scroll.visibleOrigin;
		^Rect(o.x / zoom, o.y / zoom, scroll.bounds.width / zoom, scroll.bounds.height / zoom)
	}

	// ------------------------------------------------------------- the view

	// Zoom to z, keeping the point about - in the rack's coordinates, the
	// middle of what is seen when nil - where it is on the screen. Every
	// view goes where the zoom puts it; the rack itself never changes.
	zoom_ { |z, about|
		var o = scroll.visibleOrigin, at, old = zoom;
		z = z.clip(zoomSteps.first, zoomSteps.last);
		if (z == zoom) { ^this };
		about = about ?? { this.prSeen.center };
		at = (about * old) - o;
		zoom = z;
		views.do(_.prPlaceView);
		commentViews.do(_.prPlaceView);
		this.prResizeCanvas;
		scroll.visibleOrigin = this.prClipOrigin((about * zoom) - at);
		this.prRefreshCables;
		this.prRefreshZoom;
	}

	// a step in or out (dir 1 or -1) from the zoom there is
	zoomStep { |dir, about|
		var z = if (dir > 0) {
			zoomSteps.detect { |s| s > (zoom + 0.001) } ? zoomSteps.last
		} {
			zoomSteps.reverse.detect { |s| s < (zoom - 0.001) } ? zoomSteps.first
		};
		this.zoom_(z, about);
	}

	// everything on the rack in view, at a zoom no bigger than 1
	fitAll {
		var box = this.prContentBox, seen;
		box ?? { ^this.zoom_(1) };
		seen = scroll.bounds;
		this.zoom_(((seen.width - 40) / (box.width + 40)).min((seen.height - 40) / (box.height + 40))
			.min(1).max(zoomSteps.first));
		this.centre;
	}

	// back to the middle of what is on the rack, at the zoom there is
	centre {
		var box = this.prContentBox, c;
		c = box !? { box.center } ?? { Point(0, 0) };
		scroll.visibleOrigin = this.prClipOrigin((c * zoom) - Point(scroll.bounds.width / 2, scroll.bounds.height / 2));
	}

	// the box round every module and comment, or nil
	prContentBox {
		var rects = views.values.collect(_.bounds) ++ commentViews.values.collect(_.bounds)
			++ NdefRack.boxes.values.collect { |b| b[\rect] };
		if (rects.isEmpty) { ^nil };
		^rects.inject(rects.first, { |a, r| a.union(r) })
	}

	prClipOrigin { |p|
		^Point(p.x.clip(0, (canvas.bounds.width - scroll.bounds.width).max(0)),
			p.y.clip(0, (canvas.bounds.height - scroll.bounds.height).max(0)))
	}

	prRefreshZoom {
		zoomButton !? { zoomButton.states = [["%%".format((zoom * 100).round.asInteger, "%"), colText, colPanel]] };
	}

	// ------------------------------------------------- selection, from a view

	// a click on a module: it alone, or with shift in or out of the rest
	prClickSelect { |key, shift|
		canvas.focus(true);
		this.selectBox(nil);
		this.selectCable(nil);
		this.selectComment(nil);
		case
		{ shift } {
			this.select(if (selection.includes(key)) { selection.reject(_ == key) } { selection ++ [key] })
		}
		{ selection.includes(key).not } { this.select([key]) };
	}

	prGroupMoveBegin { |point|
		groupMove = (start: point, moved: false, origins: IdentityDictionary.new);
		selection.do { |k|
			views.at(k) !? { |v|
				groupMove[\origins].put(k, v.origin);
				v.front;
				this.prRaised(k);
			}
		};
		this.prOverlayToFront;
	}

	prGroupMove { |point|
		var delta;
		groupMove ?? { ^this };
		delta = point - groupMove[\start];
		delta = Point(delta.x.round(10), delta.y.round(10));
		// nothing goes past the top or the left of the rack
		groupMove[\origins].do { |o|
			delta = Point(delta.x.max(o.x.neg), delta.y.max(o.y.neg))
		};
		if (delta != Point(0, 0)) { groupMove[\moved] = true };
		// the bends of cables between moved modules come along
		groupMove[\delta] = delta;
		groupMove[\origins].keysValuesDo { |k, o|
			views.at(k) !? { |v| v.moveTo(o + delta) }
		};
		this.prRefreshCables;
	}

	// A click on a module of a selection that did not move it makes that
	// module the whole selection - the way to come out of a group.
	prGroupMoveEnd { |key, shift|
		groupMove ?? { ^this };
		if (groupMove[\moved]) {
			groupMove[\origins].keysDo { |k|
				views.at(k) !? { |v| NdefRack.positions.put(k, v.origin) }
			};
			this.prMoveBends(groupMove[\origins].keys, groupMove[\delta]);
			this.prResizeCanvas;
		} {
			if (shift.not) { this.select([key]) }
		};
		groupMove = nil;
	}

	// a cable bent by hand between two modules moved together keeps its shape
	prMoveBends { |keys, delta|
		if (delta.isNil or: { delta == Point(0, 0) }) { ^this };
		NdefRack.bends.keysValuesDo { |k, b|
			if (keys.includes(k[0]) and: { keys.includes(k[1]) }) {
				NdefRack.bends.put(k, this.prShiftBend(b, delta))
			}
		};
	}

	prShiftBend { |b, delta|
		^if (b.size == 3) { [b[0] + delta.x, b[1] + delta.y, b[2] + delta.x] } { b.collect(_ + delta.x) }
	}

	// the selected modules' cables are drawn on the overlay
	prSelectionChanged { |old|
		(old ++ selection).asSet.do { |k| views.at(k) !? (_.refresh) };
		overlay !? { overlay.refresh };
	}

	// a view made, rebuilt or brought forward is on top of the others
	prRaised { |key|
		zOrder.remove(key);
		zOrder.addFirst(key);
	}

	prList { |keys|
		^case
		{ keys.size == 0 } { "nothing" }
		{ keys.size <= 3 } { keys.collect(_.asString).join(", ") }
		{ "% modules".format(keys.size) }
	}

	// ------------------------------------------------------------- modules

	prSync {
		var mods = NdefRack.modules;
		var made = [];
		views.keys.asArray.do { |key|
			if (mods.at(key) !== views.at(key).module) { this.prRemoveView(key) }
		};
		NdefRack.names.do { |key|
			if (views.at(key).isNil and: { mods.at(key).notNil }) {
				this.prMakeView(key);
				made = made.add(key);
			}
		};
		if (made.notEmpty) {
			this.prPlace(made);
			this.prOverlayToFront;
			this.prNoteOverlap(made);
		};
		selection = selection.select { |k| mods.at(k).notNil };
		liveHistory.keys.asArray.do { |k| if (mods.at(k).isNil) { liveHistory.removeAt(k) } };
		this.prResizeCanvas;
	}

	// Modules made over each other - hand placed closer than they are wide,
	// a patch from before modules changed size - are left where they are;
	// the hint bar says so.
	prNoteOverlap { |made|
		var keys = views.keys.asArray;
		var hit = made.any { |k|
			var b = views.at(k).bounds;
			keys.any { |o| o != k and: { views.at(o).bounds.intersects(b) } }
		};
		if (hit) { hints.note("modules overlap - arrange tidies them") };
	}

	prMakeView { |key|
		var v = NdefRackModuleView(this, NdefRack.modules.at(key));
		// none yet: prPlace finds one, with the others made in the same frame
		NdefRack.positionOf(key) !? { |pos| v.moveTo(pos) };
		views.put(key, v);
		this.prRaised(key);
		inputCache.put(key, NdefRack.modules.at(key).inputs);
		^v
	}

	prRemoveView { |key|
		views.removeAt(key) !? (_.remove);
		zOrder.remove(key);
		inputCache.removeAt(key);
	}

	prRebuildView { |key|
		if (views.at(key).notNil) {
			this.prRemoveView(key);
			if (NdefRack.modules.at(key).notNil) {
				this.prMakeView(key);
				this.prPlace([key]);
				this.prOverlayToFront;
			};
		};
	}

	// Places for the modules among keys that have none: beside what they are
	// cabled to, the way the signal flows, or - cabled to nothing with a
	// place - as a group in the first free spot in view. See NdefRackLayout.
	prPlace { |keys|
		var origins = IdentityDictionary.new, width;
		keys = keys.select { |k| NdefRack.positionOf(k).isNil and: { views.at(k).notNil } };
		if (keys.isEmpty) { ^this };
		views.keysValuesDo { |k, v| if (keys.includes(k).not) { origins.put(k, v.origin) } };
		width = scroll.bounds.width;
		// a scroll view not laid out yet is no width at all
		if (width < 100) { width = window.bounds.width - 20 };
		this.prLayout.place(origins,
			Rect(scroll.visibleOrigin.x / zoom, scroll.visibleOrigin.y / zoom, width / zoom, scroll.bounds.height / zoom)
		).keysValuesDo { |k, p|
			views.at(k).moveTo(p);
			// straight into the table: the rack's moveTo would echo back here
			NdefRack.positions.put(k, p);
		};
	}

	prLayout {
		var boxes = IdentityDictionary.new;
		views.keysValuesDo { |k, v|
			var ins = IdentityDictionary.new;
			v.module.ordered.do { |p| v.inputJack(p[\name]) !? { |j| ins.put(p[\name], j) } };
			// the ports' centres; a read goes into the title's left edge
			boxes.put(k, (size: v.size, out: v.outputJack, ins: ins,
				read: Point(0, NdefRackModuleView.titleH div: 2), rate: v.module.rate));
		};
		^NdefRackLayout(NdefRack.names, boxes, NdefRack.cables)
	}

	prResizeCanvas {
		var right = 0, bottom = 0, w, h;
		((views.values ++ commentViews.values).collect(_.bounds) ++ NdefRack.boxes.values.collect { |b| b[\rect] }).do { |r|
			right = right.max(r.right);
			bottom = bottom.max(r.bottom);
		};
		w = ((right + 400) * zoom).max(scroll.bounds.width);
		h = ((bottom + 300) * zoom).max(scroll.bounds.height);
		if (canvas.bounds.width != w or: { canvas.bounds.height != h }) {
			canvas.bounds = Rect(0, 0, w, h);
			cableLayer.bounds = Rect(0, 0, w, h);
			boxLayer.bounds = Rect(0, 0, w, h);
			overlay.bounds = Rect(0, 0, w, h);
		};
	}

	prModuleMoved { |key, final|
		this.prRefreshCables;
		if (final) { this.prResizeCanvas };
	}

	prFollowPosition { |key|
		var v = views.at(key);
		var pos = NdefRack.positionOf(key);
		if (v.notNil and: { pos.notNil } and: { v.origin != pos }) {
			v.moveTo(pos);
			this.prRefreshCables;
			this.prResizeCanvas;
		};
	}

	prFollowPositions { views.keys.do { |key| this.prFollowPosition(key) } }

	// Left to right by how far each module is from a source: a module with
	// nothing patched in stands in the first column, a module fed by it in
	// the next, and so on. A loop is cut where it is found. Each column is
	// ordered by the height of what it is cabled to, which uncrosses most
	// cables. On the same 10 pixel grid a dragged module snaps to.
	prArrange {
		// bends made for the old places mean nothing in the new ones
		NdefRack.clearBends;
		this.prLayout.arrange(Point(20, 20)).keysValuesDo { |key, p|
			NdefRack.positions.put(key, p);
			views.at(key).moveTo(p);
		};
		this.prRefreshCables;
		this.prResizeCanvas;
	}

	// The modules in keys laid out by the layout rules
	// (NdefRackLayout:tidy), from the top left corner of where they are now:
	// the main audio path on a row, each other part on a row under it. Only
	// the cables between them count; nothing else moves, and the bends of
	// those cables go.
	arrangeModules { |keys|
		var corner, res;
		keys = (keys ? selection).asArray.select { |k| views.at(k).notNil };
		if (keys.size < 2) { ^this };
		corner = keys.collect { |k| views.at(k).origin }.inject(nil, { |a, p|
			if (a.isNil) { p } { Point(a.x.min(p.x), a.y.min(p.y)) }
		});
		corner = Point(corner.x.round(10), corner.y.round(10));
		NdefRack.bends.keys.asArray.do { |b|
			if (keys.includes(b[0]) and: { keys.includes(b[1]) }) { NdefRack.bend(b[0], b[1], b[2], nil) }
		};
		res = this.prLayout.tidy(keys, corner);
		res.keysValuesDo { |key, p|
			NdefRack.positions.put(key, p);
			views.at(key).moveTo(p);
		};
		NdefRack.changed(\positions);
		this.prRefreshCables;
		this.prResizeCanvas;
	}

	// ------------------------------------------------------------- the menu

	prAddMenu { |point| ^Menu(*this.prAddActions(point)) }

	// a submenu per category, the OF categories (ofwave, ofsynth, ...) together
	// in one "of" submenu with the prefix dropped, and the library reload; a
	// bus module is made on no bus, and picks one on its face
	prAddActions { |point|
		var menuFor = { |cat, title|
			Menu(*NdefRackLib.keysIn(cat).collect { |key|
				MenuAction(key.asString, { this.prMakeModule(key, point) })
					.toolTip_(NdefRackLib.help(key) ? "")
			}).title_(title)
		};
		var isOf = { |cat| cat.asString.beginsWith("of") };
		var cats = NdefRackLib.categories.reject(isOf).collect { |cat| menuFor.(cat, cat.asString) };
		var ofCats = NdefRackLib.categories.select(isOf);
		if (ofCats.notEmpty) {
			cats = cats.add(Menu(*ofCats.collect { |cat|
				menuFor.(cat, cat.asString.drop(2))
			}).title_("of"))
		};
		^cats ++ [
			MenuAction.separator,
			MenuAction("reload the library", { NdefRackLib.reload })
		]
	}

	prMakeModule { |kind, point|
		var px = NdefRack.make(kind);
		px !? {
			NdefRackBrowser.noteUsed(kind);
			point !? { NdefRack.positions.put(px.key, Point(point.x.round(10), point.y.round(10))) };
			// in now, rather than at the next poll
			NdefRack.scan;
			this.select([px.key]);
		};
	}

	// ---------------------------------------------------- the module library

	// the panel down the left shown, or folded away - remembered for the
	// next window (NdefRackBrowser.shown)
	showBrowser { |flag = true|
		NdefRackBrowser.shown = flag;
		browser.view.visible = flag;
		browserButton !? { browserButton.value = flag.binaryValue };
		if (flag.not) { canvas.focus(true) };
	}

	// a global point - the mouse, dragged from the panel - on the rack, or
	// nil when it is not over what is seen of it
	prRackPointAt { |global|
		var p = global - canvas.mapToGlobal(Point(0, 0));
		var seen = Rect(scroll.visibleOrigin.x, scroll.visibleOrigin.y,
			scroll.bounds.width, scroll.bounds.height);
		^if (seen.containsPoint(p)) { p / zoom }
	}

	// a library module dragged from the panel, over the rack at point (in
	// the rack's coordinates) or off it (nil)
	prLibraryDrag { |key, point|
		libDrag = (key: key, point: point);
		overlay.refresh;
	}

	// let go: made where the ghost was, its title under the mouse - off the
	// rack, nothing
	prLibraryDrop { |key, point|
		libDrag = nil;
		overlay.refresh;
		point !? { this.prMakeModule(key, Point((point.x - 20).max(0), (point.y - 11).max(0))) };
	}

	prRefreshPatches {
		if (this.isClosed) { ^this };
		patchPaths = NdefRack.patches;
		patchMenu.items = ["load patch"] ++ patchPaths.collect { |p|
			PathName(p).fileNameWithoutExtension
		};
		patchMenu.value = 0;
	}

	// ---------------------------------------------------------------- cables

	prCableFromOutput { |key, point|
		cableDrag = (mode: \out, from: key, point: point, start: point);
		this.prCableBegun;
	}

	// a plugged input is a cable to pick up; an empty one, a cable to start
	prCableFromInput { |key, param, point|
		var m = NdefRack.modules.at(key);
		var src = m !? { m.source(param) };
		cableDrag = if (src.notNil) {
			(mode: \carry, from: NdefRack.prModuleKey(src), to: key, param: param,
				point: point, start: point)
		} {
			(mode: \in, to: key, param: param, point: point, start: point)
		};
		this.prCableBegun;
	}

	// A cable is loose: every port it can land on grows (the views draw
	// that), the hint bar says what letting go would do. A carried cable
	// leaves the layer behind the modules for the overlay.
	prCableBegun {
		dragTarget = nil;
		views.do(_.refresh);
		this.prRefreshCables;
		hints.string = this.prDragHint;
	}

	// Escape while a cable is loose: it goes, and nothing is patched or
	// unplugged - a picked up cable is still where it was.
	prCableCancel {
		cableDrag = nil;
		dragTarget = nil;
		this.prCableDone;
		hints.note("cable let go - nothing changed");
	}

	prCableDone {
		views.do(_.refresh);
		this.prRefreshCables;
	}

	// A click that did not move: the cable stays on the mouse, armed, no
	// button held. The frame moves its end with the mouse; the next click
	// lands it (prArmedClick), on the empty rack or escape lets it go.
	prCableArm {
		cableDrag[\armed] = true;
		dragTarget = nil;
		canvas.focus(true);
		this.prCableDone;
		hints.string = this.prDragHint;
	}

	// the next click with a cable armed: on a target it connects as a drop
	// there would; anywhere else - a picked up cable back on its own port
	// included - it lets the cable go and nothing changes
	prArmedClick { |point|
		var d = cableDrag;
		d ?? { ^this };
		this.prCableMove(point);
		if (dragTarget.isNil or: { d[\mode] == \carry and: { dragTarget == [d[\to], d[\param]] } }) {
			this.prCableCancel
		} {
			this.prCableEnd(point)
		};
	}

	// what the hint bar says while a cable is armed, whatever is under the
	// mouse - nil while none is
	prArmedHint { ^if (this.armedCable.notNil) { this.prDragHint } }

	// Every frame while a cable is armed: its end follows the mouse - or the
	// check's stand-in for it. Learn mode turned on, or a module at either end
	// gone, lets it go.
	prFollowArmed {
		var d = cableDrag, p;
		if (d.isNil or: { d[\armed] != true }) { ^this };
		if (NdefRackMapper.learning or: {
			[d[\from], d[\to]].any { |k| k.notNil and: { views.at(k).isNil } }
		}) {
			^this.prCableCancel
		};
		p = hoverOverride ?? { this.prCursorPoint };
		if (p.notNil and: { p != d[\point] }) { this.prCableMove(p) };
	}

	// What letting go would do, in words: lfo → oct of lpf - replaces env
	prDragHint {
		var d = cableDrag, t = dragTarget;
		var src, dst, param;
		d ?? { ^nil };
		if (t.isNil and: { d[\armed] == true }) {
			^switch(d[\mode],
				\out, { "% → click an input, or a module's title for its first input - a click on the empty rack, or escape, lets go".format(d[\from]) },
				\in, { "→ % of %: click the module to patch from - a click on the empty rack, or escape, lets go".format(d[\param], d[\to]) },
				{ "% of %: click another input to move it - a click on the empty rack, or escape, leaves it where it was"
					.format(d[\param], d[\to]) }
			)
		};
		if (t.isNil) {
			^switch(d[\mode],
				\out, { "% → drop on an input, or on a module's title for its first input - escape lets go".format(d[\from]) },
				\in, { "→ % of %: drop on the module to patch from - escape lets go".format(d[\param], d[\to]) },
				{ "% of %: drop on the empty rack to unplug it, on another input to move it - escape puts it back"
					.format(d[\param], d[\to]) }
			)
		};
		if (d[\mode] == \in) {
			src = t[0]; dst = d[\to]; param = d[\param];
		} {
			src = d[\from]; dst = t[0]; param = t[1];
		};
		if (d[\mode] == \carry and: { t == [d[\to], d[\param]] }) {
			^"% → % of %: where it is - let go to leave it".format(src, param, dst)
		};
		^views.at(dst) !? { |v| v.dropHint(src, param) }
	}

	prCableMove { |point|
		var old = dragTarget;
		cableDrag ?? { ^this };
		cableDrag[\point] = point;
		// 4 px from the press, it is a drag: letting go drops it, never arms it
		if (cableDrag[\start].notNil and: { (point - cableDrag[\start]).rho >= 4 }) {
			cableDrag[\moved] = true
		};
		dragTarget = if (cableDrag[\mode] == \in) {
			this.prOutputAt(point, cableDrag[\to]) !? { |key| [key, \out] }
		} {
			this.prInputAt(point, cableDrag[\from])
		};
		if (old != dragTarget) {
			old !? { views.at(old[0]) !? (_.refresh) };
			dragTarget !? { views.at(dragTarget[0]) !? (_.refresh) };
			// the cable a drop would replace is drawn dashed, on the layer
			cableLayer.refresh;
			hints.string = this.prDragHint;
		};
		overlay.refresh;
	}

	prCableEnd { |point|
		var d = cableDrag;
		var input, output, old;
		// let go where it was pressed, never 4 px away: a click - the cable
		// stays on the mouse for the next one (not in learn mode, where a
		// click on a port arms the control instead)
		if (d.notNil and: { d[\armed] != true } and: { d[\moved] != true }
			and: { d[\start].notNil } and: { (point - d[\start]).rho < 4 }
			and: { NdefRackMapper.learning.not }) {
			^this.prCableArm
		};
		input = this.prInputAt(point, d !? { d[\from] });
		output = this.prOutputAt(point, d !? { d[\to] });
		old = dragTarget;
		cableDrag = nil;
		dragTarget = nil;
		d !? {
			switch(d[\mode],
				\out, { input !? { NdefRack.connect(d[\from], input[0], input[1]) } },
				\in, { output !? { NdefRack.connect(output, d[\to], d[\param]) } },
				\carry, {
					// dropped where it was: nothing happens; elsewhere: it moves;
					// on the empty rack: it is unplugged
					if (input != [d[\to], d[\param]]) {
						NdefRack.disconnect(d[\to], d[\param]);
						if (input.notNil and: { d[\from].notNil }) {
							NdefRack.connect(d[\from], input[0], input[1])
						};
					};
				}
			);
		};
		dirtyCables = true;
		this.prCableDone;
		if (d.notNil) { hints.clear };
	}

	// The input under point, on the topmost view there (zOrder). Over a
	// title, the input a drop there lands on: the first unplugged main input
	// of the rate the cable carries - from is the module it comes from - else
	// the first main input, else none (NdefRackModuleView:titleInput). Never
	// on the title of the module the cable comes from: a click there after
	// arming is a select or a move, not a patch into itself - that stays
	// possible on one of its own input rows.
	prInputAt { |point, from|
		zOrder.do { |key|
			var v = views.at(key), local;
			if (v.notNil and: { v.bounds.containsPoint(point) }) {
				local = point - v.origin;
				if (local.y < NdefRackModuleView.titleH) {
					if (key == from) { ^nil };
					^v.titleInput(from) !? { |param| [key, param] }
				};
				^v.inputAt(local) !? { |param| [key, param] }
			}
		};
		^nil
	}

	// The module whose output is under point: its output zone - or, for a
	// cable from an input (from is the module it starts on), anywhere on any
	// other module, since a module has one output. On its own module only
	// the output zone counts, so a click on an empty port patches nothing.
	// The topmost view under point answers (zOrder).
	prOutputAt { |point, from|
		zOrder.do { |key|
			var v = views.at(key);
			if (v.notNil and: { v.bounds.containsPoint(point) }) {
				^if (v.isOverOutput(point - v.origin) or: { from.notNil and: { key != from } }) { key }
			}
		};
		^nil
	}

	// where a cable meets a view: the output on its right edge, an input on
	// its left, each level with its port
	prOutPoint { |key| ^views.at(key) !? { |v| v.origin + Point(v.size.x, v.outputJack.y) } }

	prInPoint { |key, param|
		^views.at(key) !? { |v| v.inputJack(param) !? { |p| v.origin + Point(0, p.y) } }
	}

	// --------------------------------------------------------------- drawing

	// one cell of the grid, a dot every 20 pixels
	prGridImage {
		^Image(20, 20).draw {
			Pen.fillColor = colGrid;
			Pen.fillRect(Rect(0, 0, 1.5, 1.5));
		}
	}

	// The layer behind the modules: every fixed cable, and the reads.
	prDrawCables {
		drawnStyle = cableStyle;
		Pen.capStyle = 1;
		Pen.joinStyle = 1;
		if (showReads) {
			cableCache.do { |c| if (c[\kind] == \read) { this.prDrawRead(c) } }
		};
		cableCache.do { |c|
			if (c[\kind] == \map and: { this.prIsCarried(c).not }) {
				this.prDrawMap(c, cableAlpha, 0, false, this.prIsReplaced(c))
			}
		};
	}

	// the cable a drop on the loose cable's target would replace
	prIsReplaced { |c|
		^cableDrag.notNil and: { cableDrag[\mode] != \in } and: { dragTarget.notNil }
			and: { c[\to] == dragTarget[0] } and: { c[\param] == dragTarget[1] }
			and: { c[\from] != cableDrag[\from] }
	}

	// Above the modules: the cables of the selected modules and the one under
	// the mouse, at full strength and a little wider, their ports ringed - then
	// the loose cable, then the band.
	prDrawOverlay {
		Pen.capStyle = 1;
		Pen.joinStyle = 1;
		cableCache.do { |c|
			if (c[\kind] == \map and: { this.prIsCarried(c).not } and: {
				selection.includes(c[\from]) or: { selection.includes(c[\to]) }
					or: { this.prSameCable(c, hover) } or: { this.prSameCable(c, selectedCable) }
			}) {
				this.prDrawMap(c, 1, 1.5, true)
			}
		};
		selectedCable !? { |c| this.prDrawHandles(c) };
		cableDrag !? { |d|
			// over a target the loose end snaps to the port it would land on -
			// a title drop shows its row - and covers none of the row's words
			var end = dragTarget !? { |t|
				if (d[\mode] == \in) { this.prOutPoint(t[0]) } { this.prInPoint(t[0], t[1]) }
			} ? d[\point];
			switch(d[\mode],
				\out, { this.prDrawCable(this.prOutPoint(d[\from]), end, colAccent, this.prCableWidth(d[\from])) },
				\in, { this.prDrawCable(end, this.prInPoint(d[\to], d[\param]), colAccent, 2) },
				\carry, {
					this.prDrawCable(
						d[\from] !? { this.prOutPoint(d[\from]) } ?? { this.prInPoint(d[\to], d[\param]) },
						end, colAccent, this.prCableWidth(d[\from]))
				}
			)
		};
		// a module dragged from the library: where it will land
		libDrag !? { |d|
			d[\point] !? { |p|
				var r = Rect(p.x - 20, p.y - 11, 190, NdefRackModuleView.titleH + 24);
				Pen.fillColor = colPanel.copy.alpha_(0.6);
				Pen.fillRect(r);
				Pen.width = 1;
				Pen.lineDash = FloatArray[4, 3];
				Pen.strokeColor = colAccent;
				Pen.strokeRect(r);
				Pen.lineDash = FloatArray[1, 0];
				Pen.stringLeftJustIn(d[\key].asString, Rect(r.left + 10, r.top, r.width - 20, NdefRackModuleView.titleH),
					boldFont, colText);
			}
		};
		band !? {
			var r = Rect.fromPoints(band[\start], band[\point]);
			Pen.fillColor = colAccent.copy.alpha_(0.08);
			Pen.fillRect(r);
			Pen.width = 1;
			Pen.strokeColor = colAccent.copy.alpha_(0.7);
			Pen.strokeRect(r);
		};
	}

	// A menu of the palette, "none" first, the current one ticked; action
	// gets the name chosen, nil for none.
	colorMenu { |current, action|
		^Menu(*([MenuAction("none", { action.value(nil) }).checked_(current.isNil)] ++
			paletteNames.collect { |name|
				MenuAction(name.asString, { action.value(name) }).checked_(current == name)
			}))
	}

	// a colour name as a Color - nil for none, or a name the palette lacks
	paletteColor { |name| ^name !? { palette.at(name.asSymbol) } }

	// a cable as NdefRack.cables has it, from the output's edge to the input's
	prDrawMap { |c, alpha = 1, extra = 0, ringed = false, dashed = false|
		var a = this.prOutPoint(c[\from]), b = this.prInPoint(c[\to], c[\param]);
		var color = this.rateColor(c[\from]);
		var stripe = this.paletteColor(NdefRack.cableColorOf(c[\from], c[\to], c[\param]));
		if (a.isNil or: { b.isNil }) { ^this };
		// a cable with a colour of its own a pixel wider, so its stripes read
		this.prDrawCable(a, b, color, this.prCableWidth(c[\from]) + extra + if (stripe.notNil) { 1 } { 0 },
			this.prIsWide(c[\from]), alpha, dashed, this.prRouteOf(c), stripe);
		if (ringed) {
			// the ports at its ends: 8 px in from each edge
			Pen.width = 1.5;
			Pen.strokeColor = colAccent;
			[a - Point(8, 0), b + Point(8, 0)].do { |p| Pen.strokeOval(Rect.aboutPoint(p, 7, 7)) };
		};
	}

	prIsCarried { |c|
		^cableDrag.notNil and: { cableDrag[\mode] == \carry }
			and: { c[\to] == cableDrag[\to] } and: { c[\param] == cableDrag[\param] }
	}

	// a cable from this module carries more than one channel - voices, stereo
	prIsWide { |key|
		^(key !? { NdefRack.modules.at(key) !? { |m| m.numChannels > 1 } }) == true
	}

	// how wide a cable from this module is drawn: audio 3 px, control 2,
	// twice that when it carries more than one channel
	prCableWidth { |key|
		var m = key !? { NdefRack.modules.at(key) };
		var w = if (m.notNil and: { m.isAudio }) { 3 } { 2 };
		^if (this.prIsWide(key)) { w * 2 } { w }
	}

	// A cable is a flat curve, coloured by what flows in it, over a dark casing
	// 2 px wider. One that carries more than one channel is twice as wide,
	// with a dark line down its middle, so it reads as two. No dots at its
	// ends: the ports are the ends.
	prDrawCable { |a, b, color, width = 2, wide = false, alpha = 1, dashed = false, route, stripe|
		if (a.isNil or: { b.isNil }) { ^this };
		// the one a drop would replace: dashed until the mouse is let go
		if (dashed) {
			^Pen.use {
				Pen.lineDash = FloatArray[5, 4];
				Pen.width = width;
				Pen.strokeColor = color.copy.alpha_(alpha * 0.8);
				this.prPath(a, b, route);
				Pen.stroke;
			}
		};
		Pen.width = width + 2;
		Pen.strokeColor = Color.grey(0.063).alpha_(alpha);
		this.prPath(a, b, route);
		Pen.stroke;
		Pen.width = width;
		Pen.strokeColor = color.copy.alpha_(alpha);
		this.prPath(a, b, route);
		Pen.stroke;
		// a colour of its own: stripes of it over what it carries, so audio
		// and control still show
		stripe !? {
			Pen.use {
				Pen.capStyle = 0;
				Pen.lineDash = FloatArray[3, 3];
				Pen.width = width;
				Pen.strokeColor = stripe.copy.alpha_(alpha);
				this.prPath(a, b, route);
				Pen.stroke;
			}
		};
		if (wide) {
			Pen.width = 1.2;
			Pen.strokeColor = Color.black.alpha_(0.45 * alpha);
			this.prPath(a, b, route);
			Pen.stroke;
		};
	}

	// a route's corners as a line with rounded corners - else the curve
	prPath { |a, b, route|
		var pts;
		if (route.isNil) { ^this.prCurve(a, b) };
		pts = NdefRackRouter.simplify(route);
		if (pts.size < 2) { ^this.prCurve(a, b) };
		Pen.moveTo(pts[0]);
		// sclang ranges count down: (1..0) is two corners, not none
		if (pts.size > 2) {
			(1 .. pts.size - 2).do { |i|
				var p = pts[i];
				var r = [6, (p - pts[i - 1]).rho / 2, (pts[i + 1] - p).rho / 2].minItem;
				Pen.arcTo(p, pts[i + 1], r);
			};
		};
		Pen.lineTo(pts.last);
	}

	// The selected cable's handles: a small square in the middle of each run a
	// drag moves.
	prDrawHandles { |c|
		var pts = if (cableStyle == \routed) { this.prRouteOf(c) };
		pts ?? { ^this };
		NdefRackRouter.movable(pts).do { |i|
			var m = (pts[i] + pts[i + 1]) / 2;
			Pen.fillColor = colAccent;
			Pen.fillRect(Rect.aboutPoint(m, 4, 4));
			Pen.width = 1;
			Pen.strokeColor = colJackHole;
			Pen.strokeRect(Rect.aboutPoint(m, 4, 4));
		};
	}

	// ------------------------------------------------------ the flow chart

	// A cable's corners as the router has them, while cables are drawn as a
	// flow chart - nil otherwise, or for a cable the window does not show.
	prRouteOf { |c|
		if (cableStyle != \routed) { ^nil };
		^this.prRoutes.at(this.prRouteKey(c))
	}

	prRouteKey { |c| ^[c[\from], c[\to], if (c[\kind] == \read) { \read } { c[\param] }] }

	// Every cable's way, found once per change: the modules' boxes, the
	// cables' ends, the bends - kept in NdefRack, or the one being dragged,
	// or moved with a group of modules being dragged.
	prRoutes {
		var rects, list;
		routeCache !? { ^routeCache };
		rects = IdentityDictionary.new;
		views.keysValuesDo { |k, v| rects.put(k, Rect.fromPoints(v.origin, v.origin + v.size)) };
		// comments too: a cable goes round the text rather than behind it
		commentViews.keysValuesDo { |k, v| rects.put(k, v.bounds) };
		list = cableCache.collect { |c|
			var a = this.prOutPoint(c[\from]);
			var b = if (c[\kind] == \read) { this.prReadPoint(c[\to]) } { this.prInPoint(c[\to], c[\param]) };
			(key: this.prRouteKey(c), from: c[\from], to: c[\to], a: a, b: b,
				bend: if (c[\kind] == \map) { this.prBendFor(c) })
		}.select { |e| e[\a].notNil and: { e[\b].notNil } };
		routeCache = NdefRackRouter(rects).routeAll(list);
		^routeCache
	}

	prBendFor { |c|
		var b;
		if (bendDrag.notNil and: { this.prSameCable(c, bendDrag[\cable]) } and: { bendDrag[\bend].notNil }) {
			^bendDrag[\bend]
		};
		b = NdefRack.bendOf(c[\from], c[\to], c[\param]);
		if (b.notNil and: { groupMove.notNil } and: { groupMove[\delta].notNil }
			and: { groupMove[\origins].at(c[\from]).notNil } and: { groupMove[\origins].at(c[\to]).notNil }) {
			b = this.prShiftBend(b, groupMove[\delta])
		};
		^b
	}

	// where a read goes in: the title's left edge
	prReadPoint { |key|
		^views.at(key) !? { |v| v.origin + Point(0, NdefRackModuleView.titleH * 0.5) }
	}

	// a cable as it is drawn, as a line of points: its route, the straight
	// line, or points along its curve
	prPathOf { |c|
		var a = this.prOutPoint(c[\from]), b = this.prInPoint(c[\to], c[\param]);
		if (a.isNil or: { b.isNil }) { ^nil };
		^this.prRouteOf(c) ?? {
			if (cableStyle == \straight) { [a, b] } { this.prCablePoints(a, b) }
		}
	}

	// The two control points of the curve from a (an output) to b (an input),
	// for drawing and hit testing alike: tangents level with the ports, a
	// cable going forwards eased by half its run, one going backwards looped
	// out of its output and into its input so it shows. Straight: none.
	prControlPoints { |a, b|
		var d;
		if (cableStyle == \straight) { ^[a, b] };
		d = if (b.x >= (a.x + 20)) {
			((b.x - a.x).abs * 0.5).clip(30, 120)
		} {
			((b.x - a.x).abs * 0.5 + 60).clip(60, 160)
		};
		^[a + Point(d, 0), b - Point(d, 0)]
	}

	prCurve { |a, b|
		var cp = this.prControlPoints(a, b);
		Pen.moveTo(a);
		Pen.curveTo(b, cp[0], cp[1]);
	}

	// an Ndef read inside a source function: thin, dashed, from the output's
	// edge into the title's left edge
	prDrawRead { |c|
		var a = this.prOutPoint(c[\from]);
		var b = this.prReadPoint(c[\to]);
		if (a.isNil or: { b.isNil }) { ^this };
		Pen.use {
			Pen.width = 1.5;
			Pen.lineDash = FloatArray[4, 4];
			Pen.strokeColor = colDim.copy.alpha_(cableAlpha);
			this.prPath(a, b, this.prRouteOf(c));
			Pen.stroke;
		};
	}

	// ------------------------------------------- the preview over a cable

	// the cable under the mouse, an Event as NdefRack.cables has them, or nil
	hoveredCable { ^hover }

	// the cable whose preview is up - once the mouse has rested on it for
	// previewDelay seconds - or nil
	previewCable { ^preview }

	// what the hint bar says over a cable at a point of the rack, or nil
	cableHintAt { |point|
		^this.prCableAt(point) !? { |c|
			views.at(c[\to]) !? { |v|
				v.cableHint(c[\param]) ++ if (cableStyle == \routed) {
					" - click to select it, drag to move where it bends"
				} { " - click to select it" }
			}
		}
	}

	// for checks: look at this point of the rack instead of the mouse; nil gives
	// the mouse back
	prHoverOverride { |point| hoverOverride = point }

	// Every frame: which cable the mouse is over - hit-tested against the
	// curves on the open rack, since no mouse-over event comes from a view
	// that takes no mouse - and, once the mouse has rested on it for
	// previewDelay, a preview of its signal beside the mouse. Moving the mouse
	// puts the preview away; resting starts the count again. It goes too the
	// moment its cable is not under the mouse any more - unplugged, a module
	// deleted, moved away - and while anything is being dragged. The cable
	// under the mouse is drawn on the overlay, above the modules.
	prHover { |point|
		var now = Main.elapsedTime;
		var old = hover;
		hover = if (point.notNil and: { cableDrag.isNil } and: { band.isNil } and: { groupMove.isNil }
			and: { bendDrag.isNil }) {
			this.prCableAt(point)
		};
		if ((old.isNil and: { hover.isNil }).not and: { this.prSameCable(old, hover).not }) { overlay.refresh };
		if (point.isNil or: { restPoint.isNil } or: { (point - restPoint).rho > 3 }) {
			restPoint = point;
			restSince = now;
			preview = nil;
		};
		if (preview.notNil and: { this.prSameCable(preview, hover).not }) { preview = nil };
		if (preview.isNil and: { hover.notNil } and: { (now - restSince) >= previewDelay }) {
			preview = hover;
			previewPoint = point;
		};
		this.prUpdateProbe;
		this.prPlacePreview;
	}

	prSameCable { |a, b|
		^a.notNil and: { b.notNil } and: { a[\from] == b[\from] } and: { a[\to] == b[\to] }
			and: { a[\param] == b[\param] }
	}

	// A cable as the node maps have it now, between modules the window shows:
	// the cache of cables is a frame old at most, the node map is the truth.
	prCableIsThere { |c|
		var src = NdefRack.modules.at(c[\from]), dst = NdefRack.modules.at(c[\to]);
		^src.notNil and: { dst.notNil } and: { views.at(c[\from]).notNil } and: { views.at(c[\to]).notNil }
			and: { dst.source(c[\param]) === src.proxy }
	}

	// The cable nearest a point, within a few pixels of its curve, or nil -
	// on the open rack only: over a module the cable is behind it, and the
	// module is what the mouse is on. Not within 14 px of its ends either,
	// where it meets its ports' modules.
	prCableAt { |point|
		var best, bestDist = 7;
		if (views.any { |v| v.bounds.containsPoint(point) }) { ^nil };
		if (commentViews.any { |v| v.bounds.containsPoint(point) }) { ^nil };
		cableCache.do { |c|
			var a, b, pts;
			if (c[\kind] == \map and: { this.prIsCarried(c).not } and: { this.prCableIsThere(c) }) {
				a = this.prOutPoint(c[\from]);
				b = this.prInPoint(c[\to], c[\param]);
				if (a.notNil and: { b.notNil } and: { point.dist(a) >= 14 } and: { point.dist(b) >= 14 }) {
					// a route's corners, or a Bezier's four points: the line
					// stays inside their box
					pts = this.prRouteOf(c) ?? { [a, b] ++ this.prControlPoints(a, b) };
					if (this.prPointsBox(pts).insetBy(-8, -8).containsPoint(point)) {
						this.prPathOf(c).doAdjacentPairs { |q1, q2|
							var d = this.prSegmentDistance(point, q1, q2);
							if (d < bestDist) { bestDist = d; best = c };
						};
					};
				};
			};
		};
		^best
	}

	prPointsBox { |pts|
		var l = pts.collect(_.x).minItem, t = pts.collect(_.y).minItem;
		^Rect(l, t, pts.collect(_.x).maxItem - l, pts.collect(_.y).maxItem - t)
	}

	// points along a cable as it is drawn: the curve prCurve makes
	prCablePoints { |a, b, n = 24|
		var cp = this.prControlPoints(a, b);
		var c1 = cp[0], c2 = cp[1];
		^(0 .. n).collect { |i|
			var t = i / n, u = 1 - t;
			(a * (u ** 3)) + (c1 * (3 * u * u * t)) + (c2 * (3 * u * t * t)) + (b * (t ** 3))
		}
	}

	prSegmentDistance { |p, q1, q2|
		var d = q2 - q1;
		var len2 = (d.x * d.x) + (d.y * d.y);
		var t;
		if (len2 == 0) { ^(p - q1).rho };
		t = ((((p.x - q1.x) * d.x) + ((p.y - q1.y) * d.y)) / len2).clip(0, 1);
		^(p - (q1 + (d * t))).rho
	}

	// An audio cable needs a capture of its source on the server - a probe,
	// made when its preview opens and let go a second after it closes, at once
	// when its module goes, or made again when the source changes width. A
	// control cable needs nothing: its history is read from shared memory
	// anyway.
	prUpdateProbe {
		var src = preview !? { NdefRack.modules.at(preview[\from]) };
		var now = Main.elapsedTime;
		if (probe.notNil and: { NdefRack.modules.at(probe.module.name) !== probe.module }) { this.prFreeProbe };
		if (src.notNil and: { src.isAudio }) {
			if (probe.isNil or: { probe.module !== src } or: { probe.channels != (src.numChannels ? 1).clip(1, 16) }) {
				this.prFreeProbe;
				probe = NdefRackDisplay(\probe, src);
			};
			probeIdle = nil;
			probe.poll;
		} {
			probe !? {
				probeIdle = probeIdle ? now;
				if ((now - probeIdle) > 1) { this.prFreeProbe };
			};
		};
	}

	prFreeProbe {
		probe !? { probe.free };
		probe = nil;
		probeIdle = nil;
	}

	prMakePreview {
		previewView = UserView(canvas, Rect(0, 0, 10, 10))
			.acceptsMouse_(false)
			.visible_(false)
			.drawFunc_({ this.prDrawPreview });
	}

	// beside where the mouse rests, inside what is visible of the rack, redrawn
	// every frame while it is up
	prPlacePreview {
		var size, seen, x, y;
		if (preview.isNil) { ^this.prHidePreview };
		size = this.prPreviewSize(preview);
		seen = Rect(scroll.visibleOrigin.x, scroll.visibleOrigin.y, scroll.bounds.width, scroll.bounds.height);
		// on the canvas, in its pixels: the picture is not zoomed
		x = (previewPoint.x * zoom) + 16;
		y = (previewPoint.y * zoom) + 16;
		if ((x + size.x) > seen.right) { x = (previewPoint.x * zoom) - 16 - size.x };
		if ((y + size.y) > seen.bottom) { y = (previewPoint.y * zoom) - 16 - size.y };
		previewView.bounds = Rect(x.max(seen.left + 2), y.max(seen.top + 2), size.x, size.y);
		if (previewShown.not) {
			previewShown = true;
			previewView.visible = true;
			previewView.front;
		};
		previewView.refresh;
	}

	// AppClock only, like every Qt call. Its picture is cleared as well: a
	// UserView keeps the last one it drew, and shows it again if anything
	// shows the view. Every frame without a preview checks the view is hidden,
	// so nothing that shows it can leave it up.
	prHidePreview {
		preview = nil;
		previewView !? {
			if (previewShown or: { previewView.visible }) {
				previewShown = false;
				previewView.visible = false;
				previewView.refresh;
			}
		};
	}

	// one row per channel - one for a mono cable spread over voices
	prPreviewRows { |c|
		var src = NdefRack.modules.at(c[\from]);
		^if (c[\spread] == true) { 1 } { (src !? (_.numChannels) ? 1).clip(1, 16) }
	}

	prPreviewRowH { |rows| ^if (rows > 8) { 15 } { 20 } }

	prPreviewSize { |c|
		var rows = this.prPreviewRows(c);
		^Point(260, 28 + (rows * this.prPreviewRowH(rows)))
	}

	prDrawPreview {
		var c = preview, src, dst, b, rows, rowH, color, audio, data;
		if (c.isNil) { ^this };
		src = NdefRack.modules.at(c[\from]);
		dst = NdefRack.modules.at(c[\to]);
		if (src.isNil or: { dst.isNil }) { ^this };
		b = Rect(0, 0, previewView.bounds.width, previewView.bounds.height);
		color = this.cableColor(c[\to], c[\param]);
		Pen.fillColor = colTitle.copy.alpha_(0.96);
		Pen.addRoundedRect(b.insetBy(0.5, 0.5), 5, 5);
		Pen.fill;
		Pen.width = 1;
		Pen.strokeColor = color.copy.alpha_(0.8);
		Pen.addRoundedRect(b.insetBy(0.5, 0.5), 5, 5);
		Pen.stroke;
		Pen.stringAtPoint("% → %  %".format(c[\from], c[\to], c[\param]), 7 @ 4, font, colText);
		Pen.stringInRect(this.prPreviewNote(c, src, dst), Rect(7, 4, b.width - 14, 16), font, colDim,
			QAlignment(\right));
		rows = this.prPreviewRows(c);
		rowH = this.prPreviewRowH(rows);
		audio = src.isAudio;
		data = if (audio) {
			probe !? { |p| if (p.module === src) { p.data } }
		} {
			liveHistory.at(c[\from])
		};
		rows.do { |i|
			var r = Rect(24, 24 + (i * rowH), b.width - 84, rowH - 3);
			var text;
			Pen.stringAtPoint((i + 1).asString, Point(7, r.top + ((r.height - 14) / 2)), font, colDim);
			Pen.fillColor = colTrack;
			Pen.fillRect(r);
			text = if (audio) {
				this.prDrawWave(r, data !? { data[i] }, color)
			} {
				this.prDrawHistory(r, data, i, color)
			};
			text !? {
				Pen.stringInRect(text, Rect(r.right + 4, r.top + ((r.height - 14) / 2), b.width - r.right - 10, 14),
					font, colText, QAlignment(\right))
			};
		};
		if (data.isNil) {
			Pen.stringCenteredIn(this.prPreviewMissing(src), Rect(24, 24, b.width - 84, rows * rowH),
				font, colDim);
		};
	}

	prPreviewNote { |c, src, dst|
		^case
		{ c[\spread] == true } { "one signal into all % voices".format(dst.voices) }
		{ src.voices > 1 } { "% voices".format(src.voices) }
		{ src.numChannels > 1 } { "% channels".format(src.numChannels) }
		{ src.isAudio } { "audio" }
		{ "control" }
	}

	prPreviewMissing { |src|
		^case
		{ NdefRack.server.serverRunning.not } { "no server running" }
		{ src.isAudio } { "listening..." }
		{ "control values need a local server" }
	}

	// A short stretch of an audio channel, drawn to fill its row whatever its
	// level - the shape is what a row is for; the level is the peak it answers,
	// printed beside it. Under -60 dB it is a flat line.
	prDrawWave { |r, vals, color|
		var mid = r.center.y, half = (r.height / 2) - 1;
		var n = r.width.asInteger.max(2);
		var peak, scale;
		Pen.width = 1;
		Pen.strokeColor = colGrid;
		Pen.line(Point(r.left, mid), Point(r.right, mid));
		Pen.stroke;
		if (vals.isNil or: { vals.isEmpty }) { ^nil };
		peak = vals.collect(_.abs).maxItem;
		scale = if (peak < 0.001) { 0 } { 0.9 / peak };
		Pen.width = 1.2;
		Pen.strokeColor = color;
		n.do { |i|
			var v = vals[(i / (n - 1) * (vals.size - 1)).round.asInteger] ? 0;
			var p = Point(r.left + i, mid - ((v * scale).clip(-1, 1) * half));
			if (i == 0) { Pen.moveTo(p) } { Pen.lineTo(p) };
		};
		Pen.stroke;
		^"pk " ++ this.prValueText(peak)
	}

	// the last historyLength frames of a control channel, newest on the right,
	// stretched to fill the row; answers the latest value, to print
	prDrawHistory { |r, hist, i, color|
		var vals, lo, hi;
		if (hist.isNil or: { hist.isEmpty }) { ^nil };
		vals = hist.collect { |frame| frame[i] ? 0 };
		lo = vals.minItem;
		hi = vals.maxItem;
		Pen.width = 1.2;
		Pen.strokeColor = color;
		vals.do { |v, k|
			var x = r.right - ((vals.size - 1 - k) * (r.width / (historyLength - 1).max(1)));
			var y = if ((hi - lo) < 1e-6) { r.center.y } {
				r.bottom - 1 - ((v - lo) / (hi - lo) * (r.height - 2))
			};
			if (k == 0) { Pen.moveTo(Point(x, y)) } { Pen.lineTo(Point(x, y)) };
		};
		Pen.stroke;
		^this.prValueText(vals.last)
	}

	prValueText { |v|
		^case
		{ v.abs >= 1000 } { (v / 1000).round(0.01).asString ++ "k" }
		{ v.abs >= 100 } { v.round(1).asInteger.asString }
		{ v.abs >= 10 } { v.round(0.1).asString }
		{ v.round(0.001).asString }
	}

	// ------------------------------------------------------------- the frame

	// A SkipJack, not a Routine: cmd-period clears AppClock, and a plain
	// Routine would leave the window frozen after the first one.
	prStartUpdater {
		updater = SkipJack({
			try { this.prFrame } { |err|
				("NdefRack window: " ++ err.errorString).warn;
				// whatever went wrong, the preview over a cable is not left up
				try { this.prHidePreview; this.prFreeProbe };
			};
		}, { 1 / frameRate }, { window.isClosed }, "NdefRackGUI");
	}

	// A UserView keeps its picture in a pixmap made at the pixel ratio of the
	// screen it was on when last resized, and draws it at the ratio of the
	// screen it is on now: moved from the laptop's 2x to a 1x monitor, every
	// module and cable showed at twice its size, a quarter of it in view.
	// Only a resize makes the pixmap again - so each view is resized by a
	// pixel, and back.
	prRemakePictures {
		([boxLayer, cableLayer, overlay, previewView] ++ views.values.collect(_.view) ++ commentViews.values.collect(_.view)).do { |uv|
			var b;
			if (uv.notNil and: { uv.isClosed.not }) {
				b = uv.bounds;
				uv.bounds = b.resizeBy(1, 0);
				uv.bounds = b;
				uv.refresh;
			}
		};
	}

	// where every view is, to tell when the cables must be drawn again
	prLayerKey {
		^zOrder.collect { |k| views.at(k) !? { |v| [k, v.origin] } }.asArray
	}

	prFrame {
		var refresh = IdentitySet.new;
		this.prKeepWriting;
		if (movedAt.notNil and: { (Main.elapsedTime - movedAt) > 0.25 }) {
			movedAt = nil;
			this.prRemakePictures;
		};
		// cableStyle set from code: the menu follows, the cables are drawn again
		if (drawnStyle != cableStyle) {
			drawnStyle = cableStyle;
			styleMenu !? { styleMenu.value = cableStyles.indexOf(cableStyle) ? 0 };
			this.prRefreshCables;
		};
		if (dirtyAll) {
			dirtyAll = false;
			this.prSync;
			dirtyCables = true;
			// the counts beside the names
			browser.prFrame(true);
		} {
			browser.prFrame(false)
		};
		if (dirtyComments) { this.prSyncComments };
		if (dirtyBoxes) {
			dirtyBoxes = false;
			if (selectedBox.notNil and: { NdefRack.boxes.at(selectedBox).isNil }) { selectedBox = nil };
			boxLayer.refresh;
			refresh.addAll(views.keys);
			this.prResizeCanvas;
		};
		if (dirtyModules.notEmpty) {
			dirtyModules.copy.do { |key| this.prRebuildView(key) };
			dirtyModules.clear;
			dirtyCables = true;
		};
		// a new value only redraws its module; a new cable redraws the cables
		dirtyState.copy.do { |key|
			var m = NdefRack.modules.at(key);
			var inputs = m !? { m.inputs };
			refresh.add(key);
			// a compact module whose controls left or came back to their
			// defaults shows other rows: a view of the new size
			views.at(key) !? { |v| if (v.isStale) { this.prRebuildView(key); dirtyCables = true } };
			if (inputs != inputCache.at(key)) {
				inputCache.put(key, inputs);
				dirtyCables = true;
			};
		};
		dirtyState.clear;
		refresh.addAll(dirtyMonitor);
		dirtyMonitor.clear;
		// the cables before anything that asks where they are
		if (dirtyCables) {
			dirtyCables = false;
			cableCache = NdefRack.cables;
			refresh.addAll(views.keys);
			this.prRefreshCables;
			// the selected cable unplugged, or a module at its end gone
			if (selectedCable.notNil and: { this.prCableIsThere(selectedCable).not }) {
				selectedCable = nil;
				bendDrag = nil;
			};
		};
		// a view moved, made or gone by any way at all: the cables follow
		this.prLayerKey !? { |k| if (k != layerKey) { layerKey = k; this.prRefreshCables } };
		this.prReadLive(refresh);
		// BusRecorder's sources or take changed: the marks, and the button
		if (recDirty) {
			recDirty = false;
			refresh.addAll(views.keys);
			recButton !? { recButton.value = (NdefRack.recorder !? { |r| r.isRecording } ? false).binaryValue };
		};
		// a binding made or gone, learn mode, follow: the marks on the controls
		if (ktlDirty) {
			ktlDirty = false;
			refresh.addAll(views.keys);
			learnButton !? { learnButton.value = NdefRackMapper.learning.binaryValue };
		};
		// an armed cable's end follows the mouse
		this.prFollowArmed;
		// a loose cable by the edge of what is seen: the rack scrolls
		this.prAutoScroll;
		// the cable under the mouse, and its preview
		this.prHover(hoverOverride ?? { this.prCursorPoint });
		// the pictures that arrived since the last frame, and a request for the next
		refresh.addAll(displaysDue);
		displaysDue.clear;
		views.do { |v| v.display !? { |d| d.poll({ displaysDue.add(v.key) }) } };
		refresh.do { |key| views.at(key) !? (_.refresh) };
		this.prRefreshStatus;
	}

	// Control rate outputs, read from shared memory: the module whose output
	// moved redraws its lights, and every module it feeds redraws its row.
	// The last historyLength frames of each are kept for the preview over a
	// cable, so it shows where a control signal has been, not one number.
	prReadLive { |refresh|
		var srv = NdefRack.server;
		var shm = srv.serverRunning and: { srv.hasShmInterface };
		NdefRack.modules.keysValuesDo { |key, m|
			var px = m.proxy;
			var vals;
			if (shm and: { m.isControl } and: { px.isPlaying } and: { px.index.notNil }) {
				vals = try { px.bus.getnSynchronous(px.numChannels) };
				// seen in long checks: now and then an entry came back as a
				// Function or a Char, and liveRange then threw on every frame
				if (vals.isKindOf(Array).not or: { vals.any { |v| v.isNumber.not } }) { vals = nil };
			};
			vals !? {
				var h = liveHistory.at(key) ?? { liveHistory.put(key, List.new); liveHistory.at(key) };
				h.add(vals);
				if (h.size > historyLength) { h.removeAt(0) };
			};
			if (vals != liveCache.at(key)) {
				liveCache.put(key, vals);
				refresh.add(key);
				cableCache.do { |c| if (c[\from] == key) { refresh.add(c[\to]) } };
			};
		};
	}

	// While a cable is loose and its end is within 30 px of the edge of what
	// is seen - or past it - the rack scrolls 12 px a frame that way. The
	// mouse stays where it is on the screen, so the end moves on the rack.
	prAutoScroll {
		var p, o, seen, dx = 0, dy = 0, to;
		p = cableDrag !? { cableDrag[\point] };
		if (p.isNil) { ^this };
		// an armed cable with the mouse off the rack: no button is held, the
		// mouse went elsewhere - the rack stays where it is
		if (cableDrag[\armed] == true and: { (hoverOverride ?? { this.prCursorPoint }).isNil }) { ^this };
		o = scroll.visibleOrigin;
		seen = Rect(o.x, o.y, scroll.bounds.width, scroll.bounds.height);
		p = p * zoom;
		if (p.x < (seen.left + 30)) { dx = -12 };
		if (p.x > (seen.right - 30)) { dx = 12 };
		if (p.y < (seen.top + 30)) { dy = -12 };
		if (p.y > (seen.bottom - 30)) { dy = 12 };
		if (dx == 0 and: { dy == 0 }) { ^this };
		to = Point((o.x + dx).clip(0, (canvas.bounds.width - seen.width).max(0)),
			(o.y + dy).clip(0, (canvas.bounds.height - seen.height).max(0)));
		if (to != o) {
			scroll.visibleOrigin = to;
			this.prCableMove((p + (to - o)) / zoom);
		};
	}

	prRefreshStatus {
		var srv = NdefRack.server;
		var n = NdefRack.modules.size;
		var cables = cableCache.count { |c| c[\kind] == \map };
		var str = "% module% · % cable% · %".format(
			n, if (n == 1) { "" } { "s" },
			cables, if (cables == 1) { "" } { "s" },
			if (srv.serverRunning) {
				"server % running".format(srv.name)
			} {
				"server % not running - the Ndefs are there, and wait for it".format(srv.name)
			}
		);
		if (selection.notEmpty) { str = str + "· % selected".format(selection.size) };
		case
		{ NdefRackMapper.armed.notNil } {
			str = str + "· learn: move a control for % %".format(*NdefRackMapper.armed)
		}
		{ NdefRackMapper.learning } { str = str + "· learn: click a control, button or gate" };
		if (NdefRackKtl.isFollowing) {
			str = str + "· % turns %".format(NdefRackKtl.followKtl, NdefRackKtl.focused ? "the selected module")
		};
		NdefRack.recorder !? { |rec|
			if (rec.isRecording) {
				str = str + "· recording %:%%".format(rec.duration.asInteger div: 60,
					(rec.duration.asInteger % 60).asString.padLeft(2, "0"),
					if (rec.isPaused) { ", paused" } { "" })
			}
		};
		if (str != lastStatus) {
			lastStatus = str;
			statusText.string = str;
		};
	}
}
