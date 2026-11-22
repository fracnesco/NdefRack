// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackKtl - a controller on the rack, through the Modality toolkit
//
// An element of an MKtl moves and a control of an Ndef is set - the same
// Ndef(\lpf).set(\cutoff, ...) you would type, through the module, so a
// control with voices sets every voice. Nothing goes through the window: it
// only shows the bar where the controller left it, as it would after
// your own set. So all of it works with the window closed.
//
//   MKtl(\m1, "akai-midimix");
//   NdefRackKtl.bind(\m1, \kn_1_1, \lpf, \cutoff); // kn_1_1 turns lpf's cutoff
//   NdefRackKtl.learn(\lpf, \res);       // the next element anyone moves turns res
//   NdefRackKtl.follow(\m1);             // the knobs turn the module in focus
//   NdefRackKtl.unbind(\lpf, \cutoff);
//
// Three ways in:
//   bind     an element to a module's control, by their names
//   learn    the next element that moves to a control - the window's learn
//            button: click a control, move something on the controller
//   follow   a page of elements - every knob and fader, then the buttons -
//            over the controls of the module in focus, in their order: the
//            module selected last in the window, or NdefRackKtl.focus = \x
//
// A binding is between names, the MKtl's, the element's, the module's and
// the control's. One whose module is gone waits for it, the way a module's
// position does; one whose MKtl does not exist yet starts working when it
// does. An element can turn several controls; a control has one element.
// A binding comes before follow for its element while its module is there.
//
// Knobs and faders pick up: after a binding is made, after the focus moves
// and after the control was moved some other way - the mouse, your code -
// an element does nothing until it reaches the value the control has, so
// nothing jumps. takeover: \jump sets at once.
//
// A button on a gate holds it while it is down, on a trigger fires it, on a
// knob flips it between lo and hi. A knob or fader on a gate holds it above
// the middle and fires a trigger on the way up through it. A patched control
// belongs to its cable: the controller leaves it alone.
//
// Elements are listened to as a dependant (MKtlElement announces every move
// with changed(\doAction)), never through their action, so code of your own
// setting an element's action keeps working, and does not undo the rack's.
// Modality is looked up at run time; without it there are no controllers
// and nothing else changes.
// =====================================================================

NdefRackKtl {
	classvar <bindings;					// Events: (ktl:, element:, module:, param:, lo:, hi:, takeover:)
	classvar listeners;					// MKtl name -> [the MKtl, the function each element has as dependant]
	classvar <armed;					// [module, param]: what the next element moved is bound to
	classvar <learning = false;			// the window's learn mode: a click on a control arms it
	classvar <followKtl, <followPage, <followButtons;
	classvar focus;						// the module follow turns; nil: the window's last selected
	classvar followState, lastFocus;	// pickup, per control, while following
	classvar <>catchRange = 0.02;		// how near an element comes to a value to pick it up
	classvar <>takeover = \pickup;		// for new bindings: \pickup or \jump
	classvar <continuousTypes, <buttonTypes;

	*initClass {
		bindings = List.new;
		listeners = IdentityDictionary.new;
		followState = IdentityDictionary.new;
		continuousTypes = #[\knob, \slider, \encoder, \xfader, \bender, \ribbon, \joyAxis, \mouseAxis];
		buttonTypes = #[\button, \pad, \encoderButton, \switch, \key];
	}

	*modality { ^\MKtl.asClass }

	// --------------------------------------------------------------- listening

	// Listen to an MKtl - itself or its name. bind, learn and follow do this
	// for the MKtls they meet; NdefRack's poll does it for an MKtl made after
	// its bindings, or made again.
	*use { |ktl|
		var mktl = this.prMKtl(ktl, true);
		var func;
		mktl ?? { ^nil };
		if ((listeners.at(mktl.name) !? { |l| l[0] === mktl }) == true) { ^mktl };
		this.release(mktl.name);
		func = { |el, what| if (what == \doAction) { this.prMoved(mktl.name, el) } };
		mktl.elementsDict.do { |el| el.addDependant(func) };
		listeners.put(mktl.name, [mktl, func]);
		^mktl
	}

	// stop listening to an MKtl; its bindings stay
	*release { |name|
		listeners.removeAt(name.asSymbol) !? { |l|
			l[0].elementsDict.do { |el| el.removeDependant(l[1]) }
		}
	}

	*useAll { this.modality !? { |cl| cl.all.do { |k| this.use(k) } } }

	*listening { ^listeners.keys.asArray.sort }

	// ---------------------------------------------------------------- binding

	// element turns module's param. lo and hi are where the element's two ends
	// land, as fractions of the knob's range. Answers the binding.
	*bind { |ktl, element, module, param, lo = 0, hi = 1, takeover|
		var b;
		ktl = this.prName(ktl);
		module = module.asSymbol;
		param = param.asSymbol;
		bindings = bindings.reject { |x| x[\module] == module and: { x[\param] == param } };
		b = (ktl: ktl, element: element.asSymbol, module: module, param: param,
			lo: lo, hi: hi, takeover: takeover ? this.takeover);
		bindings.add(b);
		this.prMKtl(ktl, false) !? { |k| this.use(k) };
		this.changed(\bindings);
		^b
	}

	// a module's bindings, or one control's
	*unbind { |module, param|
		module = module.asSymbol;
		bindings = bindings.reject { |b|
			b[\module] == module and: { param.isNil or: { b[\param] == param.asSymbol } }
		};
		this.changed(\bindings);
	}

	*unbindElement { |ktl, element|
		ktl = this.prName(ktl);
		bindings = bindings.reject { |b| b[\ktl] == ktl and: { b[\element] == element.asSymbol } };
		this.changed(\bindings);
	}

	*clear {
		bindings = List.new;
		this.changed(\bindings);
	}

	*bindingFor { |module, param|
		module = module.asSymbol;
		param = param.asSymbol;
		^bindings.detect { |b| b[\module] == module and: { b[\param] == param } }
	}

	// ----------------------------------------------------------------- learning

	// The next element that moves, on any MKtl, turns module's param.
	*learn { |module, param|
		armed = [module.asSymbol, param.asSymbol];
		this.useAll;
		this.changed(\learn);
	}

	*cancelLearn {
		armed = nil;
		this.changed(\learn);
	}

	// the window's learn mode: on, a click on a control arms it
	*learning_ { |flag = true|
		learning = flag;
		if (flag) { this.useAll } { armed = nil };
		this.changed(\learn);
	}

	// --------------------------------------------------------------- following

	// The page's elements over the focused module's controls, in the order
	// the window draws them (ordered), and the buttons over its gates and
	// triggers. Both are element names; by default
	// every knob, fader and button the MKtl has, in its description's order -
	// on a Midimix the 24 knobs row by row, the 9 faders, then the buttons.
	*follow { |ktl, page, buttons|
		var mktl = this.prMKtl(ktl, true);
		mktl ?? { ^nil };
		this.use(mktl);
		followKtl = mktl.name;
		followPage = (page ?? { this.prElementsOf(mktl, continuousTypes) }).collect(_.asSymbol);
		followButtons = (buttons ?? { this.prElementsOf(mktl, buttonTypes) }).collect(_.asSymbol);
		followState.clear;
		this.changed(\follow);
		^mktl
	}

	*unfollow {
		followKtl = followPage = followButtons = nil;
		followState.clear;
		this.changed(\follow);
	}

	*isFollowing { ^followKtl.notNil }

	*focus { ^focus }

	*focus_ { |key|
		focus = key !? (_.asSymbol);
		this.changed(\follow);
	}

	// the module follow turns now
	*focused { ^focus ?? { NdefRack.guiObj !? { |g| g.selection.last } } }

	// the control of module m an element turns while following it, or nil
	*followParam { |m, element|
		var i;
		if (followKtl.isNil) { ^nil };
		i = followPage.indexOf(element);
		if (i.notNil) { ^this.prFollowParams(m, false)[i] };
		i = followButtons.indexOf(element);
		if (i.notNil) { ^this.prFollowParams(m, true)[i] };
		^nil
	}

	// and back: the element turning param of module m while following it
	*followElement { |m, param|
		var i;
		if (followKtl.isNil) { ^nil };
		i = this.prFollowParams(m, false).indexOf(param);
		if (i.notNil) { ^followPage[i] };
		i = this.prFollowParams(m, true).indexOf(param);
		if (i.notNil) { ^followButtons[i] };
		^nil
	}

	// What turns a control, for the window: (ktl:, element:, follow:), or nil.
	*controlOf { |module, param|
		var b = this.bindingFor(module, param);
		var m, el;
		if (b.notNil) { ^(ktl: b[\ktl], element: b[\element], follow: false) };
		if (followKtl.isNil or: { module.asSymbol != this.focused }) { ^nil };
		m = this.prModule(module.asSymbol);
		el = m !? { this.followElement(m, param.asSymbol) };
		if (el.isNil or: { this.prBound(followKtl, el) }) { ^nil };
		^(ktl: followKtl, element: el, follow: true)
	}

	// ----------------------------------------------------------------- saving

	// the bindings as a patch file keeps them
	*asArray {
		^bindings.collect { |b|
			[b[\ktl], b[\element], b[\module], b[\param], b[\lo], b[\hi], b[\takeover]]
		}.asArray
	}

	*fromArray { |list| list.do { |a| this.bind(*a) } }

	// the bindings of these modules as code: one NdefRackKtl.bind line each
	*codeLines { |names|
		var keys = names !? { names.collect(_.asSymbol) };
		^bindings.select { |b| keys.isNil or: { keys.includes(b[\module]) } }.collect { |b|
			var args = [b[\ktl], b[\element], b[\module], b[\param]].collect { |x| NdefRack.prSym(x) };
			if (b[\lo] != 0 or: { b[\hi] != 1 }) { args = args ++ [b[\lo], b[\hi]] };
			if (b[\takeover] != \pickup) { args = args.add("takeover: " ++ NdefRack.prSym(b[\takeover])) };
			"NdefRackKtl.bind(%);".format(args.join(", "))
		}.asArray
	}

	// ----------------------------------------------------------------- private

	// NdefRack's poll: an MKtl that a binding or follow names, made since, or
	// made again - and, while learning, any new one
	*prPoll {
		var cl = this.modality;
		var names;
		cl ?? { ^this };
		if (learning or: { armed.notNil }) { ^this.useAll };
		names = bindings.collect { |b| b[\ktl] }.asSet;
		followKtl !? { names.add(followKtl) };
		names.do { |n|
			cl.all.at(n) !? { |k|
				if ((listeners.at(n) !? { |l| l[0] === k }) != true) { this.use(k) }
			}
		};
	}

	*prName { |ktl|
		^(if (ktl.isKindOf(Symbol).not and: { ktl.respondsTo(\elementsDict) }) { ktl.name } { ktl }).asSymbol
	}

	*prMKtl { |ktl, warn = true|
		var cl = this.modality;
		if (cl.isNil) {
			if (warn) { "NdefRackKtl: the Modality toolkit is not installed".warn };
			^nil
		};
		if (ktl.isKindOf(cl)) { ^ktl };
		^cl.all.at(ktl.asSymbol) ?? {
			if (warn) { ("NdefRackKtl: there is no MKtl called %".format(ktl.asCompileString)).warn };
			nil
		}
	}

	// A module without starting the rack's poll: found if the rack has it, or
	// once there is an Ndef of that name - not a scan for every message of a
	// binding whose module is gone.
	*prModule { |key|
		^NdefRack.modules.at(key) ?? {
			if ((NdefRack.proxySpace !? { |sp| sp.envir.at(key) }).notNil) { NdefRack.at(key) }
		}
	}

	*prBound { |ktl, element|
		^bindings.any { |b|
			b[\ktl] == ktl and: { b[\element] == element } and: { this.prModule(b[\module]).notNil }
		} or: { this.prMapperUses(ktl, element) }
	}

	// Mapper (sc-mapper), when the rack goes through it: an element it maps
	// is taken - follow leaves it alone, as it does a bound one
	*prMapperUses { |ktl, element|
		var cl = \Mapper.asClass;
		^cl.notNil and: { cl.hasDefault } and: { cl.default.mapsOf(ktl, element).notEmpty }
	}

	// Mapper waiting for an element to learn: the move is its, not follow's
	*prMapperLearning {
		var cl = \Mapper.asClass;
		^cl.notNil and: { cl.hasDefault } and: { cl.default.isLearning }
	}

	// element names of these types, in the MKtl's order, a note pair once
	*prElementsOf { |mktl, types|
		var res = [];
		mktl.elementGroup.flat.do { |el|
			var key;
			if (types.includes(el.type)) {
				key = this.prControl(el)[\key];
				if (res.includes(key).not) { res = res.add(key) };
			}
		};
		^res
	}

	*prFollowParams { |m, button|
		^m.ordered.select { |p|
			if (button) { p[\type] == \gate or: { p[\type] == \trigger } } { p[\type] == \knob }
		}.collect { |p| p[\name] }
	}

	// An element as a control. A note pair - Modality's MKtlDesc.notePair, a
	// pad sending noteOn when pressed and noteOff when let go - is one button,
	// named as its group: bt_1_1 for bt_1_1_on and bt_1_1_off.
	*prControl { |el|
		var name = el.name.asString;
		var role = el.elemDesc !? { |d| d[\key] };
		var value = el.value;
		^case
		{ role == \on and: { name.endsWith("_on") } } {
			(key: name.drop(-3).asSymbol, button: true, press: value.isNumber and: { value > 0 }, value: value)
		}
		{ role == \off and: { name.endsWith("_off") } } {
			(key: name.drop(-4).asSymbol, button: true, press: false, value: 0)
		}
		{ buttonTypes.includes(el.type) } {
			(key: el.name, button: true, press: value.isNumber and: { value > 0.5 }, value: value)
		}
		{ (key: el.name, button: false, value: value) }
	}

	// An element moved: in Modality's thread - MIDI's, or the one that called
	// valueAction. Nothing here may throw back into it.
	*prMoved { |ktl, el|
		try { this.prHandle(ktl, el) } { |err| ("NdefRackKtl: " ++ err.errorString).warn }
	}

	*prHandle { |ktl, el|
		var c = this.prControl(el);
		var done = false;
		if (c[\value].isNumber.not) { ^this };
		if (armed.notNil) {
			// a button is learned when pressed; its release is the end of that
			if (c[\button].not or: { c[\press] }) {
				this.bind(ktl, c[\key], armed[0], armed[1]);
				armed = nil;
				this.changed(\learn);
			};
			^this
		};
		if (this.prMapperLearning) { ^this };
		bindings.do { |b|
			if (b[\ktl] == ktl and: { b[\element] == c[\key] }) {
				this.prModule(b[\module]) !? { |m|
					done = true;
					this.prApply(m, b[\param], b, c)
				}
			}
		};
		if (done.not and: { ktl == followKtl } and: { this.prMapperUses(ktl, c[\key]).not }) { this.prFollow(c) };
	}

	*prFollow { |c|
		var key = this.focused;
		var m = key !? { this.prModule(key) };
		var name;
		m ?? { ^this };
		// another module in focus: every knob picks up again
		if (key != lastFocus) {
			followState.clear;
			lastFocus = key;
		};
		name = this.followParam(m, c[\key]);
		name ?? { ^this };
		this.prApply(m, name, followState.at(name) ?? {
			followState.put(name, (lo: 0, hi: 1, takeover: takeover));
			followState.at(name)
		}, c);
	}

	// What the element does to the control; st is the binding, or follow's
	// state for the control, and keeps the pickup.
	*prApply { |m, name, st, c|
		var p = m.param(name);
		var on;
		if (p.isNil or: { m.isPatched(name) }) { ^this };
		switch(p[\type],
			\knob, {
				if (c[\button]) {
					if (c[\press]) { this.prFlip(m, name, p[\spec], st) }
				} {
					this.prTurn(m, name, p[\spec], st, c[\value])
				}
			},
			\gate, {
				on = if (c[\button]) { c[\press] } { c[\value] > 0.5 };
				// what the window's button does: 1 held, reset let go
				if (on != st[\on]) {
					st[\on] = on;
					if (on) { m.set(name, 1) } { m.reset(name) }
				}
			},
			\trigger, {
				on = if (c[\button]) { c[\press] } { c[\value] > 0.5 };
				if (on != st[\on]) {
					st[\on] = on;
					if (on) { m.set(name, 1) } { m.reset(name) }
				}
			}
		);
	}

	// A knob or fader on a knob: lo..hi of the knob's range. Picking up, it
	// waits until it comes within catchRange of where the knob is, or passes
	// it; the knob moved by anything else since is picked up again.
	*prTurn { |m, name, spec, st, x|
		var lo = st[\lo] ? 0, hi = st[\hi] ? 1;
		var held = m.value(name).asArray.first ? 0;
		var at = if (hi != lo) { (spec.unmap(held) - lo) / (hi - lo) } { 0 };
		var prev = st[\prev];
		var value;
		st[\prev] = x;
		if (st[\sent].notNil and: { held != st[\sent] }) { st[\caught] = false };
		if ((st[\takeover] ? takeover) == \pickup and: { st[\caught] != true }) {
			if ((x - at).abs <= catchRange or: {
				prev.notNil and: { (prev - at).sign != (x - at).sign }
			}) {
				st[\caught] = true
			} {
				^this
			}
		};
		value = spec.map(lo + (x * (hi - lo)));
		st[\sent] = value;
		m.set(name, value);
	}

	// a button on a knob: to hi from the lower half of lo..hi, else to lo
	*prFlip { |m, name, spec, st|
		var lo = st[\lo] ? 0, hi = st[\hi] ? 1;
		var at = spec.unmap(m.value(name).asArray.first ? 0);
		var value = spec.map(if (at > ((lo + hi) / 2)) { lo } { hi });
		st[\sent] = value;
		m.set(name, value);
	}
}
