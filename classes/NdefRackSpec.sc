// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackSpec - what range a control covers
//
// Every control in the rack needs a ControlSpec, and the rack never asks you
// for one. It looks, in this order:
//
//   1. the Ndef's own specs: JITLibExtensions' addSpec when that quark is
//      installed (its own precedence), then the spec: in the function -
//        Ndef(\a, { SinOsc.ar(\freq.kr(110, spec: [20, 2000, \exp])) });
//   2. the name in the global table, \freq.asSpec - but only when the
//      control's default fits inside it, so an lfo's \freq.kr(0.5) does
//      not get a 20 Hz floor;
//   3. a guess from the name and the default value.
//
//   NdefRackSpec.for(Ndef(\a), \freq);        // -> a ControlSpec
//   NdefRackSpec.origin(Ndef(\a), \freq);     // -> \own, \global or \guess
//   NdefRackSpec.guess(\depth, 0.3);          // -> ControlSpec(0, 1)
//
// The spec only sets what a bar reaches. A value set in code outside it
// stays what it is - the bar draws at its end and the number says the
// truth. unitFor and readout put the number into words: "1.2 kHz",
// "+0.25 oct", "10 ms", "-60.0 dB".
// =====================================================================

NdefRackSpec {

	*for { |proxy, name, default|
		^this.prLookup(proxy, name, default)[0]
	}

	*origin { |proxy, name, default|
		^this.prLookup(proxy, name, default)[1]
	}

	// step 1 alone: nil when the Ndef says nothing about this name
	*own { |proxy, name|
		var spec;
		name = name.asSymbol;
		spec = this.prHalo(proxy, name);
		if (spec.isNil and: { proxy.respondsTo(\findFirstSpecFor) }) {
			spec = proxy.findFirstSpecFor(name)
		};
		^spec !? { spec.asSpec }
	}

	// step 3 on its own, which the help file and the tests call directly
	*guess { |name, default|
		var str = name.asString.toLower;
		var vals = default.asArray.select(_.isNumber);
		var v = if (vals.isEmpty) { 0 } { vals.maxItem { |x| x.abs } };
		var lo = if (vals.isEmpty) { 0 } { vals.minItem };
		var has = { |...words| words.any { |w| str.contains(w) } };
		var unit = { |a, b| lo >= a and: { v <= b } };
		var integral = vals.notEmpty and: { vals.every { |x| x == x.round } };

		// JITLib's filter slots - wet1, mix20 - are always a 0..1 blend
		if ("^(wet|mix)[0-9]+$".matchRegexp(str)) { ^ControlSpec(0, 1, default: v) };

		case
		{ has.("freq", "cutoff", "hz") } {
			if (v >= 20) { ^ControlSpec(20, 20000, \exp, 0, v, "Hz") };
			if (v > 0) { ^ControlSpec(0.01, 100, \exp, 0, v, "Hz") };
		}
		{ has.("oct") and: { v.abs <= 4 } } {
			^ControlSpec(-4, 4, default: v)
		}
		{ has.("semi", "transp") and: { v.abs <= 24 } } {
			^ControlSpec(-24, 24, default: v)
		}
		{ has.("db") and: { v <= 12 } } {
			^ControlSpec(-60, 12, default: v, units: "dB")
		}
		{ has.("time", "attack", "decay", "release", "atk", "rel", "dur", "delay",
			"lag", "glide", "slew", "rise", "fall") } {
			if (v > 0 and: { v <= 10 }) { ^ControlSpec(0.001, 10, \exp, 0, v, "s") };
			if (v == 0) { ^ControlSpec(0, 10, 4, 0, 0, "s") };
		}
		{ has.("pan", "bal", "offset", "bias") and: { unit.(-1, 1) } } {
			^ControlSpec(-1, 1, default: v)
		}
		{ has.("step", "len", "count", "num", "chan", "voices", "bits")
			and: { integral } and: { v >= 0 } and: { v <= 64 } } {
			^ControlSpec(0, (v * 2).max(16), \lin, 1, v)
		};

		// nothing in the name: the default alone
		^case
		{ v == 0 } { ControlSpec(-1, 1, default: 0) }
		{ unit.(0, 1) } { ControlSpec(0, 1, default: v) }
		{ unit.(-1, 1) } { ControlSpec(-1, 1, default: v) }
		{ v > 1 } { ControlSpec(v / 20, v * 20, \exp, 0, v) }
		{ ControlSpec(v * 2, v.abs * 2, default: v) }
	}

	// The unit a control's value reads in - "Hz", "oct", "s", "dB", "bpm" -
	// or nil for a plain number. From the name and the spec; no module says it:
	//   Hz   a frequency spec (20..20k exp, or units Hz), the names freq,
	//        base, ref, formant, hpf, dark, rate and density, tone/bright
	//        when exp
	//   bpm  bpm
	//   oct  oct, or shift reaching no further than 4
	//   s    attack, decay, release, rise, fall, time, glen, sweep, snap,
	//        smooth, lag, predelay - unless the spec is a plain lin 0..1
	//   dB   floor, top, or thresh below 0 and up to 12
	//
	//   NdefRackSpec.unitFor(\freq, \freq.asSpec);   // -> "Hz"
	*unitFor { |name, spec|
		var lo, hi, exp, units;
		name = name.asSymbol;
		spec = spec !? { spec.asSpec };
		lo = spec !? { spec.minval } ? 0;
		hi = spec !? { spec.maxval } ? 1;
		exp = spec.notNil and: { spec.warp.isKindOf(ExponentialWarp) };
		units = (spec !? { spec.units } ? "").asString.replace(" ", "");
		^case
		{ units == "Hz" or: { exp and: { lo == 20 } and: { hi == 20000 } } } { "Hz" }
		{ #[\freq, \base, \ref, \formant, \hpf, \dark, \rate, \density].includes(name) } { "Hz" }
		{ name == \bpm } { "bpm" }
		{ #[\tone, \bright].includes(name) and: { exp } } { "Hz" }
		{ name == \oct or: { name == \shift and: { hi <= 4 } } } { "oct" }
		{ #[\attack, \decay, \release, \rise, \fall, \time, \glen, \sweep, \snap,
			\smooth, \lag, \predelay].includes(name) } {
			if (spec.notNil and: { spec.warp.isKindOf(LinearWarp) }
				and: { lo == 0 } and: { hi == 1 }) { nil } { "s" }
		}
		{ #[\floor, \top].includes(name)
			or: { name == \thresh and: { lo < 0 } and: { hi <= 12 } } } { "dB" }
		{ nil }
	}

	// A value as the window writes it, with its unit:
	//   NdefRackSpec.readout(\freq, \freq.asSpec, 1200);     // -> "1.2 kHz"
	//   NdefRackSpec.readout(\oct, nil, 0.25);               // -> "+0.25 oct"
	//   NdefRackSpec.readout(\attack, nil, 0.01);            // -> "10 ms"
	// A plain number: whole for a spec stepping by 1, two decimals below
	// 10, else one.
	*readout { |name, spec, value|
		var unit, v, step;
		if (value.isNil) { ^"" };
		if (value.isNumber.not) { ^value.asString };
		spec = spec !? { spec.asSpec };
		unit = this.unitFor(name, spec);
		v = value.asFloat;
		^switch(unit,
			"Hz", {
				case
				{ v.abs >= 1000 } { this.prTrim(this.prFixed(v / 1000, 2)) ++ " kHz" }
				{ v.abs >= 100 } { this.prFixed(v, 0) ++ " Hz" }
				{ v.abs >= 10 } { this.prFixed(v, 1) ++ " Hz" }
				{ this.prFixed(v, 2) ++ " Hz" }
			},
			"oct", { (if (v.round(0.01) > 0) { "+" } { "" }) ++ this.prFixed(v, 2) ++ " oct" },
			"s", {
				case
				{ v.abs < 0.01 } { this.prFixed(v * 1000, 1) ++ " ms" }
				{ v.abs < 1 } { this.prFixed(v * 1000, 0) ++ " ms" }
				{ v.abs < 10 } { this.prFixed(v, 2) ++ " s" }
				{ this.prFixed(v, 1) ++ " s" }
			},
			"dB", { this.prFixed(v, 1) ++ " dB" },
			"bpm", { this.prFixed(v, 0) ++ " bpm" },
			{
				step = spec !? { spec.step } ? 0;
				case
				{ step >= 1 } { this.prFixed(v, 0) }
				{ v.abs < 10 } { this.prFixed(v, 2) }
				{ this.prFixed(v, 1) }
			}
		)
	}

	// ------------------------------------------------------------- private

	// v with exactly d decimals, rounded half away from zero. Built from an
	// integer (sclang Integers are 32 bits: past 1e7 the plain asString).
	*prFixed { |v, d|
		var scale = 10 ** d, n, whole, frac, str;
		if ((v.abs * scale) >= 1e9) { ^v.asString };
		n = (v.abs * scale).round.asInteger;
		whole = n div: scale.asInteger;
		frac = n - (whole * scale.asInteger);
		str = whole.asString;
		if (d > 0) { str = str ++ "." ++ frac.asString.padLeft(d, "0") };
		^if (v < 0 and: { n > 0 }) { "-" ++ str } { str }
	}

	// "1.20" -> "1.2", "20.00" -> "20"
	*prTrim { |str|
		if (str.includes($.).not) { ^str };
		while { str.last == $0 } { str = str.drop(-1) };
		if (str.last == $.) { str = str.drop(-1) };
		^str
	}

	*prLookup { |proxy, name, default|
		var spec;
		name = name.asSymbol;
		default = default ?? { this.prDefault(proxy, name) };
		spec = this.own(proxy, name);
		if (spec.isKindOf(ControlSpec)) { ^[spec, \own] };
		spec = name.asSpec;
		if (spec.isKindOf(ControlSpec) and: { this.prFits(spec, default) }) {
			^[spec, \global]
		};
		^[this.guess(name, default), \guess]
	}

	*prDefault { |proxy, name|
		^proxy.tryPerform(\findControlName, name) !? (_.defaultValue) ? 0
	}

	*prFits { |spec, default|
		^default.asArray.every { |x|
			x.isNumber and: { x >= spec.clipLo } and: { x <= spec.clipHi }
		}
	}

	// Specs from JITLibExtensions' addSpec, looked up by name at run time so
	// nothing here needs the quark to compile. Halo keeps them in an Event
	// whose parent is the class's specs, whose parent is the global table -
	// and an Event answers for its parents, so the walk stops before the
	// global table: that one is step 2, with its own condition.
	*prHalo { |proxy, name|
		var halo = \Halo.asClass;
		var specs;
		if (halo.isNil) { ^nil };
		specs = halo.at(proxy, \spec);
		while { specs.notNil and: { specs !== Spec.specs } } {
			if (specs.keys.includes(name)) { ^specs.at(name) };
			specs = specs.tryPerform(\parent);
		};
		^nil
	}
}
