// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackCommentView - a box of text on the rack
//
//   NdefRack.addComment("the loop: seed, fold, split, tilt, back", Point(40, 20));
//
//  +---------------------------------------+
//  | the loop: seed, fold, split, tilt,    |  drag to move, double click
//  | back                                  |  to write in it, the corner
//  |                                    // |  to make it bigger or smaller
//  +---------------------------------------+
//
// One comment of NdefRack.comments, drawn by the window in the rack's
// coordinates at the window's zoom. It is the window's, like a module's
// place: nothing about it reaches an Ndef. Text wraps at the box's width.
// A click selects it - backspace then deletes it - a drag moves it on the
// window's 10 pixel grid, the bottom right corner resizes it, a double click
// opens a field to write in: cmd-return or a click elsewhere keeps what is
// written, escape leaves it as it was. Right click: write, delete.
// =====================================================================

NdefRackCommentView {
	classvar <minSize, <grip = 12;

	var <gui, <id, <view, <origin, <size, drag, <editor, draft, <finishEdit;

	*initClass { minSize = Point(60, 30) }

	*new { |gui, id| ^super.newCopyArgs(gui, id.asSymbol).init }

	init {
		var r = NdefRack.commentRect(id) ?? { Rect(20, 20, 220, 80) };
		origin = r.origin;
		size = r.extent;
		view = UserView(gui.canvas, Rect(0, 0, 10, 10))
			.drawFunc_({ Pen.scale(gui.zoom, gui.zoom); this.prDraw })
			.mouseDownAction_({ |uv, x, y, mod, button, clicks|
				this.prMouseDown((x @ y) / gui.zoom, mod, button, clicks)
			})
			.mouseMoveAction_({ |uv, x, y, mod| this.prMouseMove((x @ y) / gui.zoom, mod) })
			.mouseUpAction_({ |uv, x, y, mod| this.prMouseUp((x @ y) / gui.zoom, mod) });
		gui.hints.attach(view, { |v, x, y|
			if (this.prOnGrip((x @ y) / gui.zoom)) {
				"drag to make the comment bigger or smaller"
			} {
				"a comment - drag to move it, double click to write in it, backspace deletes the selected one"
			}
		});
		this.prPlaceView;
	}

	text { ^NdefRack.commentText(id) ? "" }

	// in the rack's coordinates, whatever the zoom
	bounds { ^Rect(origin.x, origin.y, size.x, size.y) }

	// the comment as NdefRack has it now: moved, resized or written from code
	follow {
		var r = NdefRack.commentRect(id);
		r !? {
			if (r.origin != origin or: { r.extent != size }) {
				origin = r.origin;
				size = r.extent;
				this.prPlaceView;
			}
		};
		this.refresh;
	}

	// moved for a while - a network box dragged with it in - NdefRack told after
	moveTo { |point|
		origin = point.asPoint;
		this.prPlaceView;
	}

	prPlaceView {
		var z = gui.zoom;
		if (view.isClosed.not) {
			view.bounds = Rect(origin.x * z, origin.y * z, size.x * z, size.y * z)
		};
		editor !? { if (editor.isClosed.not) { editor.bounds = Rect(0, 0, size.x * z, size.y * z) } };
	}

	refresh { if (view.isClosed.not) { view.refresh } }

	front { if (view.isClosed.not) { view.front } }

	// being written in: what is written so far is kept
	remove {
		finishEdit.value(true);
		editor !? { if (editor.isClosed.not) { editor.remove } };
		editor = nil;
		if (view.isClosed.not) { view.remove };
	}

	isEditing { ^editor.notNil and: { editor.isClosed.not } }

	// ------------------------------------------------------------ drawing

	prDraw {
		var c = NdefRackGUI;
		var face = Rect(0.5, 0.5, size.x - 1, size.y - 1);
		var selected = gui.selectedComment == id;
		var str = this.text;
		Pen.fillColor = Color(0.17, 0.17, 0.155, 0.92);
		Pen.addRoundedRect(face, 3, 3);
		Pen.fill;
		Pen.strokeColor = if (selected) { c.colAccent } { c.colEdge };
		Pen.width = if (selected) { 2 } { 1 };
		Pen.addRoundedRect(face, 3, 3);
		Pen.stroke;
		// a bar down the left: a comment, not a module
		Pen.fillColor = c.colDim;
		Pen.fillRect(Rect(1, 1, 3, size.y - 2));
		if (this.isEditing.not) {
			if (str.isEmpty) {
				Pen.stringAtPoint("double click to write", Point(10, 6), gui.font, c.colDim)
			} {
				// wrapped by hand: Qt's stringInRect does not wrap
				this.prLines(str, size.x - 18).do { |line, i|
					var y = 6 + (i * 15);
					if ((y + 14) <= (size.y - 4)) { Pen.stringAtPoint(line, Point(10, y), gui.font, c.colText) };
				}
			}
		};
		// the grip
		Pen.strokeColor = c.colDim;
		Pen.width = 1;
		[4, 8].do { |d|
			Pen.line(Point(size.x - 3, size.y - 3 - d), Point(size.x - 3 - d, size.y - 3));
		};
		Pen.stroke;
	}

	// the text as lines no wider than width: words wrapped, line breaks kept,
	// a word wider than a line on a line of its own
	prLines { |str, width|
		var lines = [];
		str.split($\n).do { |para|
			var line = "";
			para.split($ ).do { |word|
				var next = if (line.isEmpty) { word } { line ++ " " ++ word };
				if (line.notEmpty and: { next.bounds(gui.font).width > width }) {
					lines = lines.add(line);
					line = word;
				} {
					line = next
				}
			};
			lines = lines.add(line);
		};
		^lines
	}

	// ------------------------------------------------------------ the mouse

	prOnGrip { |p| ^(p.x > (size.x - grip)) and: { p.y > (size.y - grip) } }

	// a press on the comment to its release: one step for undo
	prMouseDown { |p, mod, button, clicks|
		if ((button == 1 or: { mod.isCtrl }).not) { NdefRackUndo.gestureBegin };
		^this.prMouseDownDo(p, mod, button, clicks)
	}

	prMouseUp { |p, mod|
		var res = this.prMouseUpDo(p, mod);
		NdefRackUndo.gestureEnd;
		^res
	}

	prMouseDownDo { |p, mod, button, clicks|
		gui.prCommentClicked(id);
		if (button == 1 or: { mod.isCtrl }) {
			^Menu(
				gui.prAct("write in it", { this.edit }),
				gui.prAct("delete the comment", { gui.deleteComment(id) })
			).front
		};
		if ((clicks ? 1) >= 2) { ^this.edit };
		drag = (mode: if (this.prOnGrip(p)) { \size } { \move },
			start: origin + p, origin: origin, size: size, moved: false);
		this.front;
		gui.prOverlayToFront;
	}

	prMouseMove { |p, mod|
		var d = drag, delta;
		d ?? { ^this };
		delta = (origin + p) - d[\start];
		delta = Point(delta.x.round(10), delta.y.round(10));
		if (delta != Point(0, 0)) { d[\moved] = true };
		if (d[\mode] == \move) {
			origin = Point((d[\origin].x + delta.x).max(0), (d[\origin].y + delta.y).max(0));
		} {
			size = Point((d[\size].x + delta.x).max(minSize.x), (d[\size].y + delta.y).max(minSize.y));
		};
		this.prPlaceView;
		this.refresh;
		gui.prRefreshCables;
	}

	prMouseUpDo { |p, mod|
		var d = drag;
		drag = nil;
		if (d.notNil and: { d[\moved] }) {
			NdefRack.placeComment(id, this.bounds);
		};
	}

	// ------------------------------------------------------------ writing

	// a field over the box: cmd-return or a click elsewhere keeps the text,
	// escape leaves it as it was. The window closed with the field open takes
	// the field with it before anything here hears of it, and a closed field
	// has no text to give: draft is the text as the last frame found it.
	edit {
		var z = gui.zoom;
		if (this.isEditing) { ^this };
		draft = this.text;
		finishEdit = { |keep|
			finishEdit = nil;
			this.prKeepDraft;
			if (keep) { NdefRackUndo.step { NdefRack.setComment(id, draft) } };
			{ editor !? { if (editor.isClosed.not) { editor.remove } }; editor = nil; this.refresh }.defer(0.01);
		};
		editor = TextView(view, Rect(0, 0, size.x * z, size.y * z))
			.font_(Font(gui.font.name, gui.font.size * z))
			.string_(this.text)
			.background_(Color(0.12, 0.12, 0.11))
			.stringColor_(NdefRackGUI.colText)
			.focusLostAction_({ finishEdit.value(true) })
			.keyDownAction_({ |v, char, mod, unicode, keycode, key|
				case
				{ key == 16r01000000 } { finishEdit.value(false); true }
				{ (key == 16r01000004 or: { key == 16r01000005 }) and: { mod.isCmd or: { mod.isCtrl } } } {
					finishEdit.value(true); true
				}
				{ nil }
			});
		editor.focus(true);
		this.refresh;
	}

	// every frame: the text being written, while its field is there to read
	prKeepDraft { if (this.isEditing) { draft = editor.string } }
}
