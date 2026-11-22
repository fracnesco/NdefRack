// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackHintBar - a strip that says what the mouse is over
//
// The same idea as ModuleHintBar in the module family, FxHintBar in
// ParaEQ and SDGHintBar in SuperDirtGUI, written here so this repository
// keeps depending on nothing: a line along the bottom of a window that
// describes whatever the pointer is on, so a rack full of small controls
// and ports can be read without a manual.
//
//   hints = NdefRackHintBar();
//   hints.watch(window);                    // once, after the window exists
//   window.layout = VLayout(..., hints.toggle, hints.view);
//
//   hints.attach(button, "what this button does");
//   hints.attach(readout, { "now at " ++ thing.value });     // worked out on hover
//   hints.attach(userView, { |view, x, y| whatIsAt(x, y) }); // where in the view
//
// One thing the others do not need: a module here is drawn in a single
// UserView, so its hint depends on where in the view the pointer is. A
// hint Function is called with the view and the pointer's x and y; a
// String, or a Function that takes nothing, works as it does elsewhere.
//
// Moving onto nothing in particular puts the bar back to its default,
// `note` changes that default, and `toggle` is a checkbox that folds the
// bar away.
// =====================================================================

NdefRackHintBar {
	classvar <>defaultHeight = 22;
	classvar <>maxLines = 3;		// a long hint wraps, the bar growing up to this

	var <view, <text, <>defaultString;
	var toggleBox, font, shown = true, height;

	*new { |defaultString, height| ^super.new.init(defaultString, height) }

	init { |str, h|
		defaultString = str ?? { "hover over anything in the rack and this bar says what it does" };
		font = Font(Font.defaultSansFace, 10);
		height = h ? defaultHeight;
		text = StaticText().font_(font).stringColor_(NdefRackGUI.colDim).string_(defaultString);
		view = View().background_(NdefRackGUI.colTitle).fixedHeight_(height);
		view.layout = HLayout(text).margins_([8, 1, 8, 1]);
		^this
	}

	// A child's mouse-over only arrives if the window is willing to hear
	// about it. Moving onto the window itself - the gaps between controls -
	// puts the bar back to its default, so a stale hint does not sit there
	// describing something the pointer left a while ago.
	watch { |window|
		var v = window.tryPerform(\view) ? window;
		v !? {
			v.acceptsMouseOver = true;
			v.mouseOverAction = { this.clear };
		};
		^this
	}

	// `hint` is a String, or a Function called with the view, x and y and
	// answering one. Answers `views`, so a control can be wrapped where it
	// is built.
	attach { |views, hint|
		views.asArray.do { |v|
			v !? {
				v.acceptsMouseOver = true;
				v.mouseOverAction = { |view, x, y| this.string_(hint.value(view, x, y)) };
			};
		};
		^views
	}

	// what the bar falls back to when the pointer is over nothing
	note { |str|
		str !? { defaultString = str.asString };
		this.string_(str);
	}

	// A hint longer than the bar is wide wraps onto a second and a third line
	// - a module's description, a control's - and the bar grows to hold them,
	// back to one line for the next short one.
	string_ { |str|
		text !? {
			if (text.isClosed.not) {
				str = str ? defaultString;
				text.string = str;
				view.fixedHeight = height + ((this.prLines(str) - 1) * (font.size + 4));
			}
		}
	}

	prLines { |str|
		var room = text.bounds.width - 4;
		var w = try { str.bounds(font).width } { 0 };
		if (room <= 40) { ^1 };
		// a little slack: words wrap whole, so a line holds less than its width
		^(w / (room * 0.92)).ceil.asInteger.clip(1, maxLines)
	}

	string { ^text !? (_.string) }

	clear { this.string_(nil) }

	// Kept here rather than asked of the view: a view in a window not yet
	// shown answers false, and a checkbox made then would start unticked.
	visible { ^shown }

	visible_ { |bool|
		shown = bool;
		view.visible = bool;
		toggleBox !? { |b| b.value = bool };
	}

	// A checkbox that folds the bar away; made on the first ask, the same one
	// after that. It has to sit outside the bar, or folding it would take the
	// way back with it.
	toggle {
		toggleBox ?? {
			toggleBox = CheckBox().string_("hints").value_(shown)
				.action_({ |v| shown = v.value; view.visible = v.value });
			this.attach(toggleBox, "the hint bar along the bottom - untick to fold it away");
		};
		^toggleBox
	}
}
