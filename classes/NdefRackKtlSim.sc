// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackKtlSim - a MIDI controller that is not plugged in
//
// A Modality MKtl made from its description, as for the real controller,
// with a real MIDIMKtlDevice - listening to a MIDI source that is the sim.
// The sim's knobs, faders and buttons send what the hardware sends -
// control changes, notes on and off - into MIDIIn, where CoreMIDI delivers
// what arrives from a port. From MIDIIn on, every line that runs is the
// line the hardware runs: the device's MIDIFuncs, the element, its action
// and dependants, the rack.
//
//   ~sim = NdefRackKtlSim(\m1);        // MKtl(\m1), an Akai Midimix
//   ~sim.gui;                          // its knobs, faders and buttons
//   ~sim.move(\kn_1_1, 0.5);           // CC 16 at 64, as turning the knob does
//   ~sim.press(\bt_1_1);               // note 1 on - a mute button down
//   ~sim.release(\bt_1_1);             // note 1 off
//   ~sim.free;
//
// What the language sends to the controller - an element's value_, a
// button's LED - arrives here too: the device's MIDIOut is the sim, and the
// buttons in its window light the way the Midimix's do.
//
// Any MIDI description works: NdefRackKtlSim(\nk, "korg-nanokontrol2").
// The sim's MIDI source is its own, so a real Midimix plugged in goes on
// as it was - but an MKtl name is one MKtl: free the sim before making
// MKtl(\m1) for the hardware. Bindings name the MKtl, not the device, so
// what was learned on NdefRackKtlSim(\m1) works on the real MKtl(\m1).
// =====================================================================

NdefRackKtlSim {
	classvar <all, uidCount = 0;
	// a made-up CoreMIDI uid: no port of this Mac has it
	classvar <>uidBase = 1314016000;

	var <name, <descName, <uid, <mktl, <lookupName, <leds;
	var <window, <views, watchers;		// views: control key -> its Knob, Slider or button

	*initClass { all = IdentityDictionary.new }

	*new { |name = \midimix, desc = "akai-midimix"|
		name = name.asSymbol;
		^all.at(name) ?? { super.newCopyArgs(name, desc).init }
	}

	init {
		var cl = \MKtl.asClass;
		var desc, idInfo, info;
		if (cl.isNil) {
			"NdefRackKtlSim: the Modality toolkit is not installed".warn;
			^nil
		};
		if (cl.all.at(name).notNil) {
			("NdefRackKtlSim: MKtl(%) exists already - free it, or simulate under another name"
				.format(name.asCompileString)).warn;
			^nil
		};
		desc = \MKtlDesc.asClass.new(descName);
		if (desc.isNil or: { desc.protocol != \midi }) {
			("NdefRackKtlSim: % is not a MIDI description".format(descName.asCompileString)).warn;
			^nil
		};
		// MIDI first: opening it lists the ports again, and the sim's port,
		// listed before, would be gone
		\MIDIMKtlDevice.asClass.initDevices;
		uid = uidBase + uidCount;
		uidCount = uidCount + 1;
		// an idInfo of its own, so a real Midimix plugged in is not a second
		// candidate for the sim, nor the sim one for MKtl(\x, "akai-midimix")
		idInfo = "% (sim %)".format(desc.idInfo, name);
		lookupName = ("midi_sim_" ++ name).asSymbol;
		info = \MKtlLookup.asClass.addMIDI(MIDIEndPoint(idInfo, "sim", uid), 0, \src,
			lookupName: lookupName, idInfo: idInfo);
		info.put(\desc, desc).put(\filename, descName);
		mktl = cl.new(name, lookupName);
		if (mktl.isNil or: { mktl.hasDevice.not }) {
			("NdefRackKtlSim: MKtl(%) did not open the sim's device".format(name.asCompileString)).warn;
			mktl !? (_.free);
			^nil
		};
		// what the language sends to the controller comes to the sim
		mktl.device.instVarPut(\midiOut, this);
		leds = Dictionary.new;
		views = IdentityDictionary.new;
		all.put(name, this);
	}

	free {
		this.close;
		mktl !? { mktl.free };
		\MKtlLookup.asClass !? { |cl| cl.all.removeAt(lookupName) };
		all.removeAt(name);
	}

	// ------------------------------------------------------------- playing it

	// a knob or fader to value, 0..1, as the controller sends it
	move { |key, value|
		var el = this.prElement(key, nil);
		el ?? { ^nil };
		this.prSend(el, el.deviceSpec.map(value.clip(0, 1)));
	}

	// a button down - the on of a note pair, or a button of its own
	press { |key, value = 1|
		var el = this.prElement(key, \on);
		el ?? { ^nil };
		this.prSend(el, el.deviceSpec.map(value.clip(0, 1)));
	}

	// a button let go: the off of a note pair with the velocity Akai's own
	// send, or the button's own message at 0
	release { |key|
		var off = mktl.elementsDict.at((key.asString ++ "_off").asSymbol);
		var el;
		if (off.notNil) { ^this.prSend(off, 127) };
		el = this.prElement(key, \on);
		el ?? { ^nil };
		this.prSend(el, 0);
	}

	// What the window shows, in the description's order: (key:, kind:, row:,
	// column:) - kind \knob, \slider or \button, a note pair once, under its
	// group's name, where its on element is drawn.
	controls {
		var res = [], seen = IdentitySet.new;
		mktl.elementGroup.flat.do { |el|
			var key = this.prKey(el);
			var style = el.elemDesc !? { |d| d[\style] } ? ();
			if (seen.includes(key).not and: { this.prRole(el) != \off }) {
				seen.add(key);
				res = res.add((key: key, element: el.name, row: style[\row], column: style[\column],
					kind: case
						{ #[\knob, \encoder].includes(el.type) } { \knob }
						{ #[\slider, \xfader, \bender, \ribbon].includes(el.type) } { \slider }
						{ \button }));
			}
		};
		^res
	}

	// whether the language has lit a button: what it last sent its note
	isLit { |key|
		var el = this.prElement(key, \on, false);
		var d = el !? (_.elemDesc);
		d ?? { ^false };
		^(leds.at([d[\midiChan] ? 0, d[\midiNum]]) ? 0) > 0
	}

	// ---------------------------------------------------- MIDIOut, for the device

	noteOn { |chan, num, vel| this.prLed(chan, num, vel) }

	noteOff { |chan, num, vel| this.prLed(chan, num, 0) }

	control { |chan, num, val| this.prLed(chan, num, val) }

	latency { ^0 }

	latency_ { }

	// --------------------------------------------------------------------- gui

	gui {
		var cell = 48, pad = 10;
		var controls, w, h, x, y;
		if (window.notNil and: { window.isClosed.not }) { window.front; ^this };
		controls = this.controls;
		// anything without a place in the description goes in a row of its own
		y = (controls.collect { |c| c[\row] ? 0 }.maxItem ? 0) + 2;
		x = 0;
		controls.do { |c|
			if (c[\row].isNil or: { c[\column].isNil }) {
				c[\row] = y;
				c[\column] = x;
				x = x + 1;
			}
		};
		w = (controls.collect { |c| c[\column] }.maxItem + 1) * cell + (pad * 2);
		h = (controls.collect { |c| c[\row] + if (c[\kind] == \slider) { 2 } { 1 } }.maxItem) * cell + (pad * 2);
		window = Window("% - sim of %".format(mktl, descName), Rect(80, 80, w, h + 18), false);
		window.view.background = NdefRackGUI.colBg;
		watchers = [];
		controls.do { |c|
			var rect = Rect(pad + (c[\column] * cell), pad + (c[\row] * cell), cell - 4,
				if (c[\kind] == \slider) { cell * 2 - 4 } { cell - 4 });
			views.put(c[\key], this.prView(c, rect));
		};
		StaticText(window, Rect(pad, h - 2, w - (pad * 2), 16))
			.string_("MIDI into MIDIIn from uid %, as the controller sends it".format(uid))
			.font_(Font(Font.defaultSansFace, 9)).stringColor_(NdefRackGUI.colDim);
		window.onClose = {
			watchers.do(_.remove);
			watchers = [];
			views.clear;
		};
		window.front;
	}

	close { window !? { if (window.isClosed.not) { window.close } } }

	// ----------------------------------------------------------------- private

	prView { |c, rect|
		var key = c[\key];
		var el = mktl.elementsDict.at(c[\element]);
		var label = key.asString;
		var small = Font(Font.defaultSansFace, 8);
		var view;
		^switch(c[\kind],
			\knob, {
				view = Knob(window, Rect(rect.left + 6, rect.top, rect.width - 12, rect.height - 12))
					.mode_(\vert).value_(el.value ? 0)
					.action_({ |v| this.move(key, v.value) });
				this.prLabel(label, Rect(rect.left, rect.bottom - 11, rect.width, 11), small);
				this.prFollow(el, view);
				view
			},
			\slider, {
				view = Slider(window, Rect(rect.left + 10, rect.top, rect.width - 20, rect.height - 12))
					.orientation_(\vertical).value_(el.value ? 0)
					.action_({ |v| this.move(key, v.value) });
				this.prLabel(label, Rect(rect.left, rect.bottom - 11, rect.width, 11), small);
				this.prFollow(el, view);
				view
			},
			{
				var down = false;
				UserView(window, Rect(rect.left + 3, rect.top + 8, rect.width - 6, rect.height - 16))
					.drawFunc_({ |uv|
						var b = Rect(0, 0, uv.bounds.width, uv.bounds.height);
						Pen.fillColor = case
							{ this.isLit(key) } { NdefRackGUI.colAccent }
							{ down } { NdefRackGUI.colEdge }
							{ NdefRackGUI.colKnob };
						Pen.addRoundedRect(b.insetBy(0.5, 0.5), 3, 3);
						Pen.fill;
						Pen.stringCenteredIn(label, b, small,
							if (this.isLit(key)) { Color.black } { NdefRackGUI.colText });
					})
					.mouseDownAction_({ |uv| down = true; this.press(key); uv.refresh })
					.mouseUpAction_({ |uv| down = false; this.release(key); uv.refresh })
			}
		)
	}

	prLabel { |str, rect, font|
		StaticText(window, rect).string_(str).font_(font).align_(\center)
			.stringColor_(NdefRackGUI.colDim);
	}

	// the view follows its element: moved by the sim, by code, by a binding
	prFollow { |el, view|
		watchers = watchers.add(SimpleController(el).put(\value, {
			{ if (view.isClosed.not) { view.value = el.value } }.defer
		}));
	}

	prLed { |chan, num, val|
		leds.put([chan, num], val);
		{ views.do { |v| if (v.isKindOf(UserView) and: { v.isClosed.not }) { v.refresh } } }.defer;
		this.changed(\led, chan, num, val);
	}

	prRole { |el| ^el.elemDesc !? { |d| d[\key] } }

	// a note pair under its group's name: bt_1_1 for bt_1_1_on and bt_1_1_off
	prKey { |el|
		var str = el.name.asString;
		^switch(this.prRole(el),
			\on, { if (str.endsWith("_on")) { str.drop(-3).asSymbol } { el.name } },
			\off, { if (str.endsWith("_off")) { str.drop(-4).asSymbol } { el.name } },
			{ el.name }
		)
	}

	prElement { |key, role, warn = true|
		var d = mktl.elementsDict;
		var el = (role !? { d.at((key.asString ++ "_" ++ role).asSymbol) }) ?? { d.at(key.asSymbol) };
		if (el.isNil and: { warn }) {
			("NdefRackKtlSim: % has no element %".format(mktl, key.asCompileString)).warn
		};
		^el
	}

	// The message the element listens for, into MIDIIn as from the sim's port.
	prSend { |el, value|
		var d = el.elemDesc;
		var chan = d[\midiChan] ? 0;
		var num = d[\midiNum];
		var v = value.round.asInteger;
		switch(d[\midiMsgType],
			\cc, { MIDIIn.doControlAction(uid, chan, num, v.clip(0, 127)) },
			\control, { MIDIIn.doControlAction(uid, chan, num, v.clip(0, 127)) },
			\noteOn, { MIDIIn.doNoteOnAction(uid, chan, num, v.clip(0, 127)) },
			\noteOff, { MIDIIn.doNoteOffAction(uid, chan, num, v.clip(0, 127)) },
			\polyTouch, { MIDIIn.doPolyTouchAction(uid, chan, num, v.clip(0, 127)) },
			\polytouch, { MIDIIn.doPolyTouchAction(uid, chan, num, v.clip(0, 127)) },
			\touch, { MIDIIn.doTouchAction(uid, chan, v.clip(0, 127)) },
			\bend, { MIDIIn.doBendAction(uid, chan, v.clip(0, 16383)) },
			\program, { MIDIIn.doProgramAction(uid, chan, v.clip(0, 127)) },
			{
				("NdefRackKtlSim: % sends %, which the sim does not"
					.format(el.name, d[\midiMsgType].asCompileString)).warn
			}
		);
	}

	printOn { |stream| stream << this.class.name << "(" <<< name << ")" }
}
