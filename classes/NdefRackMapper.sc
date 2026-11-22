// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackMapper - the rack's controls on Mapper (sc-mapper), when it is
// installed
//
//   Mapper.default.map(\m1, \kn_1_1, [\ndef, \lp, \freq]);  // a knob on lp's freq
//   Mapper.default.gui;          // or the window's mapper button
//
// While the rack watches its Ndefs, it is one of Mapper's providers, under
// the key \ndef: every control a controller can turn - a bar, a stepped
// bar, a switch, a gate, a trigger - is the target [\ndef, module, param].
// Moving one sets the Ndef through its module, the same Ndef(\lp).set(...)
// you would type, so a control with voices sets every voice; a patched
// control belongs to its cable and is left alone. The addresses are names,
// so a mapping waits while its module is gone and works again when it is
// back. Mapper saves the mappings in its own file, not in patch files.
//
// It is also what the window asks about controllers - learn mode, the
// control waiting for one, the element that turns a control - so the
// window is the same with Mapper or without it: with Mapper the learn
// button is Mapper's learn mode, shared with every window that can be
// mapped; without it, NdefRackKtl's. NdefRackKtl's bindings and follow
// keep working either way, and their tags still show.
//
// Mapper is looked up by name (\Mapper.asClass): nothing here needs it to
// compile.
// =====================================================================

NdefRackMapper {
	classvar <>key = \ndef;			// the provider key: the first item of every address
	classvar provider;				// the instance registered with Mapper.default
	classvar turnedOn = false;		// the rack's learn button turned Mapper's learn mode on
	classvar signature;				// the targets there were last told about

	// ------------------------------------------------------------- class side

	*mapperClass { ^\Mapper.asClass }

	// whether the rack's controllers go through Mapper
	*usesMapper { ^this.mapperClass.notNil }

	// Mapper.default, or nil without Mapper. Asking for it the first time
	// reads Mapper's file - a check sets Mapper.defaultPath and autoLoad first.
	*mapper { ^this.mapperClass !? { |cl| cl.default } }

	// whether anything can learn at all: Modality installed
	*canLearn { ^\MKtl.asClass.notNil }

	// On Mapper.default, as \ndef. NdefRack.watch does it; again is harmless.
	*register {
		var m = this.mapper;
		if (m.isNil) { ^this };
		provider = provider ?? {
			var p = this.new;
			// the rack's notifications reach it as a dependant of NdefRack
			NdefRack.addDependant(p);
			signature = p.prSignature;
			p
		};
		if (m.providers[key] !== provider) { m.register(key, provider) };
	}

	*unregister {
		if (provider.notNil and: { this.mapperClass.notNil } and: { this.mapperClass.hasDefault }) {
			if (this.mapper.providers[key] === provider) { this.mapper.unregister(key) };
		};
	}

	*isRegistered {
		^provider.notNil and: { this.mapperClass.notNil } and: { this.mapperClass.hasDefault }
			and: { this.mapper.providers[key] === provider }
	}

	*address { |module, param| ^[key, module.asSymbol, param.asSymbol] }

	// NdefRack says its modules changed: Mapper is told when the list of
	// targets did, and that values moved when a module's state did, so
	// the LEDs of switches follow
	*prRackChanged { |what|
		var sig;
		if (provider.isNil) { ^this };
		switch(what,
			\state, { provider.changed(\values) },
			\modules, { sig = provider.prSignature },
			\module, { sig = provider.prSignature }
		);
		if (sig.notNil and: { sig != signature }) {
			signature = sig;
			provider.changed(\mapTargets);
		};
	}

	// -------------------------------------------------- what the window asks

	// learn mode: a click on a control arms it
	*learning {
		^if (this.usesMapper) { this.mapper.learnMode } { NdefRackKtl.learning }
	}

	*learning_ { |bool|
		var m;
		bool = bool == true;
		if (this.usesMapper.not) { NdefRackKtl.learning = bool; ^this };
		m = this.mapper;
		if (bool and: { m.learnMode.not }) { turnedOn = true };
		m.learnMode = bool;
		if (bool.not) { turnedOn = false };
	}

	// Mapper's learn mode went off, by whoever: the rack no longer owns it
	*prLearnModeChanged {
		if (this.usesMapper and: { this.mapperClass.hasDefault } and: { this.mapper.learnMode.not }) {
			turnedOn = false
		}
	}

	// the window closes: learn mode off, if the rack turned it on - another
	// window may be learning through Mapper too
	*windowClosed {
		if (this.usesMapper.not) {
			if (NdefRackKtl.learning) { NdefRackKtl.learning = false };
			^this
		};
		if (turnedOn and: { this.mapperClass.hasDefault } and: { this.mapper.learnMode }) {
			this.mapper.learnMode = false
		};
		turnedOn = false;
	}

	// [module, param] waiting for a controller, or nil
	*armed {
		var t;
		if (this.usesMapper.not) { ^NdefRackKtl.armed };
		t = this.mapper.armed;
		^if (t.notNil and: { t.address.size == 3 } and: { t.address[0] == key }) { t.address.drop(1) }
	}

	// the next element moved on any controller turns this control
	*learn { |module, param|
		if (this.usesMapper.not) { ^NdefRackKtl.learn(module, param) };
		this.register;
		this.mapper.learn(this.address(module, param));
	}

	*cancelLearn {
		if (this.usesMapper.not) { ^NdefRackKtl.cancelLearn };
		this.mapper.cancelLearn;
	}

	// nothing turns it any more
	*forget { |module, param|
		if (this.usesMapper.not) { ^NdefRackKtl.unbind(module, param) };
		this.mapper.unmap(this.address(module, param));
		NdefRackKtl.unbind(module, param);
	}

	// What turns a control: (ktl:, element:, follow:, mapper:), or nil.
	// Mapper's mapping first, then NdefRackKtl's binding or follow.
	*controlOf { |module, param|
		var m;
		if (this.usesMapper and: { this.mapperClass.hasDefault }) {
			m = this.mapper.mapFor(this.address(module, param));
			m !? { ^(ktl: m.ktl, element: m.element, follow: false, mapper: true) };
		};
		^NdefRackKtl.controlOf(module, param)
	}

	// Mapper's window, with this control on show when it is given
	*gui { |module, param|
		var w;
		if (this.usesMapper.not) { ^nil };
		this.register;
		w = this.mapper.gui;
		if (w.notNil and: { module.notNil } and: { param.notNil }) {
			w.tryPerform(\select, this.address(module, param))
		};
		^w
	}

	// --------------------------------------------------------- the provider

	// NdefRack changed - in whatever thread changed the Ndef: nothing may
	// escape to the user's Ndef.set
	update { |who, what|
		if (who === NdefRack) {
			try { this.class.prRackChanged(what) } { |err| ("NdefRackMapper: " ++ err.errorString).warn }
		}
	}

	mapLabel { ^"ndef rack" }

	// one control as a target description, or nil: a module the rack
	// shows, a control a controller can turn
	mapTargetAt { |path|
		var name = path[0], param = path[1], m, p;
		if (name.isNil or: { param.isNil }) { ^nil };
		m = NdefRack.modules.at(name.asSymbol);
		p = m !? { m.param(param.asSymbol) };
		^p !? { this.prDescribe(m, p) }
	}

	mapTargets {
		var out = [];
		NdefRack.modules.keys.asArray.sort.do { |name|
			var m = NdefRack.modules.at(name);
			m.ordered.do { |p| this.prDescribe(m, p) !? { |d| out = out.add(d) } };
		};
		^out
	}

	// the targets as names, to tell whether they changed
	prSignature {
		^NdefRack.modules.keys.asArray.sort.collect { |name|
			[name] ++ NdefRack.modules.at(name).ordered.select { |p| this.prTurnable(p) }
				.collect { |p| [p[\name], p[\role]] }
		}
	}

	// a slider per voice is shown as an array, but it is still one control
	// a knob sets for every voice: its mapping must not go
	prTurnable { |p|
		^#[\knob, \gate, \trigger].includes(p[\type])
			or: { p[\type] == \array and: { p[\voice] == true } and: { p[\role] != \signal } }
	}

	prDescribe { |m, p|
		var name = p[\name], mod = m.name;
		var d;
		if (this.prTurnable(p).not) { ^nil };
		d = (
			path: [mod, name],
			label: "% %".format(mod, name),
			group: mod.asString,
			hint: "% of %%".format(name, mod, m.kind !? { |k| " (%)".format(k) } ? "")
		);
		// a slider per voice: the one knob it is otherwise, set on every voice
		switch(if (p[\type] == \array) { \knob } { p[\type] },
			\knob, {
				d[\kind] = if (p[\role] == \toggle) { \toggle } { \knob };
				d[\spec] = p[\spec];
				d[\get] = { m.value(name).asArray.first };
				d[\set] = { |v| if (m.isPatched(name).not) { m.set(name, v) } };
			},
			\gate, {
				// what the window's button does: 1 held, the default back let go
				d[\kind] = \toggle;
				d[\get] = { if ((m.value(name).asArray.first ? 0) > 0.5) { 1 } { 0 } };
				d[\set] = { |v|
					if (m.isPatched(name).not) {
						if (v > 0.5) { m.set(name, 1) } { m.reset(name) }
					}
				};
			},
			\trigger, {
				// fired, then the node map clean again, as the button leaves it
				d[\kind] = \trigger;
				d[\trigger] = {
					if (m.isPatched(name).not) {
						m.set(name, 1);
						SystemClock.sched(0.05, { m.reset(name); nil });
					}
				};
			}
		);
		^d
	}
}
