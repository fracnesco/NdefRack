// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackModule - one Ndef, as the rack sees it
//
// Nothing is defined twice: a module is read from its Ndef. The title is
// the key and the bus, the inputs are the controls, the ranges come from
// NdefRackSpec, and the cables are the proxies sitting in the node map.
//
//   m = NdefRack.at(\osc);
//   m.title;                     // -> "osc  ar 1"
//   m.paramNames;                // every control but out, i_out, gate, fadeTime
//   m.value(\freq);              // what the row shows
//   m.source(\fm);               // -> Ndef(\lfo), or nil when nothing is plugged in
//
// A param is an Event: name, rate, size, default, spec, a type - what kind
// of control it is - and a role, what the window draws for it (roleFor);
// help is what it does in words, when the library module says.
// Every row has an input port on the left as well.
//
//   type      what it is                          role
//   \knob     a kr control                        \bar; \stepped when its
//                                                 spec steps by 1 or more;
//                                                 \toggle when that is 0..1
//   \array    a kr control with more than one     \array: bars to draw
//             value
//   \audio    an ar control (\in.ar)              \signal: a port only
//   \trigger  a tr control (\t_x, .tr)            \trigger: a button that fires
//   \gate     a kr control called trig, gt, hold   \gate: a button held down
//             or clock, or whose spec's units
//             are "gate"
//
// ordered gives the params in the order the window draws them, in four
// bands (bands): the main inputs, timing (gates, rate, triggers), the body,
// and the tail (lo, hi, mix, level, amp, master). params keeps the order
// the Ndef declares; settings, exports and patch files use that.
//
// A library module playing several voices (NdefRack.setVoices) has a value
// per voice in its controls; such a param says voice: true. It is still one
// row, one button or one port - setting it sets every voice - unless the
// library lists it among the module's sliders, a slider per voice.
//
//   m.voices;                    // -> 4
//   m.set(\freq, 300);           // -> [300, 300, 300, 300] in the node map
//
// A bus module reads one of BusManager's buses by name, or a bus index
// (NdefRack.setBus) - or writes to it, a send; which one is read back from
// its source, as the voices are:
//
//   m.bus;                       // -> \reverb, or 16 for an index of your own
//   m.busInfo;                   // -> (name: \reverb, index: 16, channels: 2, rate: \audio)
//
// A module panning among speakers - vbap, ambi - says which setup, read
// back from its source the same way (NdefRack.setSpeakers):
//
//   m.speakers;                  // -> \octo
//   m.speakerInfo;               // -> (name: \octo, setup: NdefRackSpeakers(\octo))
//
// A module is a dependant of its Ndef, so a set, a map or a new source in
// code reaches the rack straight away. NdefRack's poll is the safety net
// for whatever does not announce itself.
// =====================================================================

NdefRackModule {
	classvar <gateNames;

	var <name, <proxy, <params, <kind, <rate, <numChannels;
	var <voices = 1, voiceKeys, sliders, <busInfo, <speakerInfo;
	var signature, state, monitorState;
	var <lastValues;			// param -> the last number or array the node map held

	// a clock input is a gate too - its button steps a sequencer by hand
	*initClass { gateNames = #[\trig, \gt, \hold, \clock] }

	*new { |name, proxy| ^super.newCopyArgs(name.asSymbol, proxy).init }

	init {
		lastValues = IdentityDictionary.new;
		this.prRead;
		proxy.addDependant(this);
	}

	// stop listening to the Ndef; the rack calls this when the module goes
	release { proxy.removeDependant(this) }

	// ----------------------------------------------------------------- reading

	title { ^"%  %%".format(name, if (voices > 1) { "×%  ".format(voices) } { "" }, this.typeString) }

	typeString {
		^case
		{ rate == \audio } { "ar %".format(numChannels) }
		{ rate == \control } { "kr %".format(numChannels) }
		{ "empty" }
	}

	isAudio { ^rate == \audio }

	isControl { ^rate == \control }

	// an Ndef that has a bus but nothing playing into it yet - for instance
	// one that was patched from before it was ever defined
	isEmpty { ^proxy.objects.isEmpty }

	isMonitoring { ^proxy.isMonitoring }

	// a library module written for voices: NdefRack.setVoices can change them
	canVoice { ^kind.notNil and: { NdefRackLib.canVoice(kind) } }

	// a library module reading one of BusManager's buses, or writing to it:
	// NdefRack.setBus can change which
	canBus { ^kind.notNil and: { NdefRackLib.canBus(kind) } }

	// whether it writes to that bus - send, replace, insert on an audio bus
	writesBus { ^kind.notNil and: { NdefRackLib.writesBus(kind, this.prBusRate) } }

	// "reads", "writes to" or "reads and writes" - an insert on a control bus
	// only reads
	busVerb { ^if (kind.notNil) { NdefRackLib.busVerb(kind, this.prBusRate) } { "reads" } }

	prBusRate { ^busInfo !? { |b| b[\rate] } }

	// its bus: the name of a BusManager bus, a bus index, or nil - what
	// NdefRack.setBus takes
	bus { ^busInfo !? { |b| b[\name] ?? { b[\index] } } }

	// a library module panning among the speakers of a setup:
	// NdefRack.setSpeakers can change which
	canSpeakers { ^kind.notNil and: { NdefRackLib.canSpeakers(kind) } }

	// the name of its speaker setup, or nil
	speakers { ^speakerInfo !? { |i| i[\name] } }

	// whether NdefRackSpeakers has that name, and these speakers under it
	speakersKnown {
		^speakerInfo.notNil and: { NdefRackSpeakers.at(speakerInfo[\name]) === speakerInfo[\setup] }
	}

	isVoiceParam { |key| ^(this.param(key) !? { |p| p[\voice] }) == true }

	param { |key|
		key = key.asSymbol;
		^params.detect { |p| p[\name] == key }
	}

	paramNames { ^params.collect { |p| p[\name] } }

	// What a row shows: a number set in the node map; on a patched input,
	// the number it held before the cable arrived; else the function's default.
	value { |key|
		var v = proxy.nodeMap.at(key);
		var default = this.param(key) !? { |p| p[\default] };
		if (this.prIsValue(v)) { ^v };
		if (v.notNil) { ^lastValues[key] ? default };
		^default
	}

	// the proxy a cable brings into this control, or nil - also when it
	// arrives once per voice, a mono cable spread over every voice
	source { |key|
		var v = proxy.nodeMap.at(key);
		^if (v.tryPerform(\nodeMapMapsToControl) == true) { v } { this.prSpreadSource(v) }
	}

	isPatched { |key| ^this.source(key).notNil }

	// A mono cable into a control with a value per voice is the source once per
	// voice, Ndef(\lfo) ! 4 in the node map: every voice hears it.
	isSpread { |key| ^this.prSpreadSource(proxy.nodeMap.at(key)).notNil }

	// What the node map holds for a cable from src into key: the proxy, or the
	// proxy for every voice when a mono source meets a control with a value per
	// voice. Array.fill, not !, so it is the same proxy every time whatever kind
	// it is - only Ndef's ! promises that.
	cableFor { |key, src|
		^if (voices > 1 and: { this.isVoiceParam(key) } and: { src.numChannels == 1 }) {
			Array.fill(voices, { src })
		} { src }
	}

	// A value for key as wide as the voices: one number on every voice, a row
	// cut to the voices or lengthened with its last value. Anything else, and
	// any control without a value per voice, as it is.
	fit { |key, value|
		if (this.isVoiceParam(key).not) { ^value };
		^case
		{ value.isNumber } { value ! voices }
		{ value.isKindOf(SequenceableCollection) and: { value.notEmpty } } {
			value.asArray.extend(voices, value.asArray.last)
		}
		{ value }
	}

	// every cable arriving here, [paramName, sourceProxy] for each
	inputs {
		var res = [];
		params.do { |p| this.source(p[\name]) !? { |src| res = res.add([p[\name], src]) } };
		^res
	}

	// proxies the source function reads by itself, Ndef.ar(\x) inside it:
	// a wire the rack can show but not unplug
	reads {
		var res = IdentitySet.new;
		proxy.objects.do { |obj| obj.tryPerform(\parents) !? { |set| res.addAll(set) } };
		^res.asArray
	}

	// What a copy of this module starts from: every control's value - a patched
	// one, the value it had before its cable - except where it is the default
	// anyway, and never a trigger, which would fire on arrival.
	copyValues {
		var res = [];
		params.do { |p|
			var v = this.value(p[\name]);
			if (v.notNil and: { v != p[\default] } and: { p[\type] != \trigger }) {
				res = res.add(p[\name]).add(v)
			};
		};
		^res
	}

	// the library's display for this module - \scope, \spectrum - or nil
	display { ^kind !? { NdefRackLib.entry(kind) !? { |e| e[\display] } } }

	// What each output channel is - ["value", "gate"] - when the library
	// module names them and the output is as wide as the names; else nil.
	outs {
		^kind !? { NdefRackLib.outsFor(kind) } !? { |names|
			if (names.size == numChannels) { names }
		}
	}

	// The node map as it stands, values only, for the code export and patch
	// files. Never a trigger: written down, it would fire when read back.
	settings {
		var res = [];
		params.do { |p|
			var v = proxy.nodeMap.at(p[\name]);
			if (this.prIsValue(v) and: { p[\type] != \trigger }) { res = res.add(p[\name]).add(v) };
		};
		^res
	}

	// ----------------------------------------------------------------- writing

	// What a bar does - on every voice, for a control with a value per voice;
	// the dependant notification brings the change back.
	set { |key, value| proxy.set(key, this.fit(key, value)) }

	// The default from the function, back where it was, and the node map left
	// as if the control had never moved. unmap comes first because unset alone
	// sends the server what the node map holds for the key - the old number
	// - instead of the default; once unmap has taken the key out, unset sends
	// the default. A patched input is left alone: unplug it first.
	reset { |key|
		var default = this.param(key) !? { |p| p[\default] };
		if (this.isPatched(key)) { ^this };
		proxy.unmap(key);
		proxy.unset(key);
		// unset leaves a control with a value per voice (an array control) on
		// the server as it was (measured 2026-11-22): send the default to the
		// running synths, the node map staying as unset left it
		if (default.isKindOf(SequenceableCollection)) {
			proxy.group !? { |g| g.set(key, default) };
		};
	}

	// ----------------------------------------------------------------- private

	prIsValue { |v|
		^v.isNumber or: { v.isKindOf(SequenceableCollection) and: { v.every(_.isNumber) } }
	}

	// an array holding one proxy in every place: the proxy, else nil
	prSpreadSource { |v|
		var first;
		if (v.isKindOf(SequenceableCollection).not or: { v.isEmpty }) { ^nil };
		first = v.first;
		^if (first.tryPerform(\nodeMapMapsToControl) == true and: { v.every { |x| x === first } }) {
			first
		}
	}

	// everything the window needs to know about the shape of this module
	prRead {
		var src;
		rate = proxy.rate;
		numChannels = proxy.numChannels;
		if (rate == \scalar) { rate = nil };
		// not proxy.source: that only ever asks slot 0
		src = proxy.objects.at(0) !? (_.source);
		kind = src !? { NdefRackLib.keyFor(src) };
		voices = src !? { NdefRackLib.voicesFor(src) } ? 1;
		busInfo = src !? { NdefRackLib.busFor(src) };
		// a bus module follows BusManager, however it was made
		busInfo !? { NdefRack.prWatchBuses };
		speakerInfo = src !? { NdefRackLib.speakersFor(src) };
		// and one with speakers follows the table of setups
		speakerInfo !? { NdefRack.prWatchSpeakers };
		voiceKeys = if (voices > 1) { NdefRackLib.voiceKeys(kind) } { [] };
		sliders = kind !? { NdefRackLib.sliders(kind) } ? [];
		params = proxy.controlNames(addNodeMap: false).collect { |cn| this.prParam(cn) };
		signature = this.prSignature;
		this.prReadState;
	}

	prParam { |cn|
		var size = cn.defaultValue.asArray.size.max(1);
		// a value per voice: one bar, ranged as the one voice's would be
		var voice = voiceKeys.includes(cn.name) and: { size == voices };
		var spec = NdefRackSpec.for(proxy, cn.name,
			if (voice) { cn.defaultValue.asArray.first } { cn.defaultValue });
		var type = this.prType(cn, size, spec, voice);
		^(
			name: cn.name,
			rate: cn.rate,
			size: size,
			default: cn.defaultValue,
			spec: spec,
			voice: voice,
			type: type,
			role: this.class.roleFor(type, spec),
			// what the library calls its stops, for a selector: nil for most
			labels: kind !? { NdefRackLib.labelsFor(kind, cn.name) },
			// what the control does, in words, from the library's params: -
			// nil for a module of your own
			help: kind !? { NdefRackLib.paramHelp(kind, cn.name) }
		)
	}

	// What the window draws for a param - its type refined by its spec, first
	// match wins: \signal (an audio input: a port, no control), \trigger,
	// \gate, \array, \toggle (a 0..1 control stepping by 1: a switch),
	// \stepped (any other control stepping by 1 or more), \bar (the rest).
	*roleFor { |type, spec|
		var step = spec.tryPerform(\step) ? 0;
		^case
		{ type == \audio } { \signal }
		{ type == \trigger } { \trigger }
		{ type == \gate } { \gate }
		{ type == \array } { \array }
		{ step == 1 and: { spec.minval == 0 } and: { spec.maxval == 1 } } { \toggle }
		{ step >= 1 } { \stepped }
		{ \bar }
	}

	// The params in the order the window shows them, in four bands - main
	// inputs, timing, body, tail - each keeping the declared order, except
	// timing's fixed one and the tail's name order. params itself is the
	// declared order, which settings, the export and patch files keep.
	ordered { ^this.bands.inject([], { |all, band| all ++ band }) }

	// [main inputs, timing, body, tail]:
	//   main inputs  in, in1.., a, b - any rate
	//   timing       the gates, then rate if there are gates or triggers,
	//                then the triggers
	//   tail         lo, hi, mix, level, amp, master - in that order
	//   body         everything else
	bands {
		var main, rest, gates, triggers, timed, rateP, timing, tail, body;
		main = params.select { |p| "^(in[0-9]*|a|b)$".matchRegexp(p[\name].asString) };
		rest = params.reject { |p| main.includes(p) };
		gates = rest.select { |p| p[\type] == \gate };
		triggers = rest.select { |p| p[\type] == \trigger };
		timed = (gates.size + triggers.size) > 0
			or: { main.any { |p| #[\gate, \trigger].includes(p[\type]) } };
		rateP = if (timed) { rest.select { |p| p[\name] == \rate } } { [] };
		timing = gates ++ rateP ++ triggers;
		rest = rest.reject { |p| timing.includes(p) };
		tail = this.class.tailNames.inject([], { |all, n|
			all ++ rest.select { |p| p[\name] == n }
		});
		body = rest.reject { |p| tail.includes(p) };
		^[main, timing, body, tail]
	}

	*tailNames { ^#[\lo, \hi, \mix, \level, \amp, \master] }

	prType { |cn, size, spec, voice = false|
		^case
		{ cn.rate == \audio } { \audio }
		{ cn.rate == \trigger } { \trigger }
		{ size > 1 and: { voice.not or: { sliders.includes(cn.name) }
			or: { NdefRack.showsVoices(name, cn.name) } } } { \array }
		{ gateNames.includes(cn.name) or: { spec.tryPerform(\units) == "gate" } } { \gate }
		{ \knob }
	}

	// Cheap enough to take four times a second for every module: what the
	// controls are, without asking for specs.
	prSignature {
		^[
			proxy.rate, proxy.numChannels, proxy.sources,
			proxy.controlNames(addNodeMap: false).collect { |cn|
				[cn.name, cn.rate, cn.defaultValue]
			}
		]
	}

	prStateSignature { ^proxy.nodeMap.asKeyValuePairs }

	prMonitorSignature { ^[proxy.isMonitoring, proxy.vol] }

	// A number replaced by a cable is remembered, so unplugging can put it
	// back; a key gone from the node map is back at its default, and so is
	// forgotten.
	prReadState {
		params.do { |p|
			var v = proxy.nodeMap.at(p[\name]);
			case
			{ this.prIsValue(v) } { lastValues[p[\name]] = v }
			{ v.isNil } { lastValues.removeAt(p[\name]) };
		};
		state = this.prStateSignature;
		monitorState = this.prMonitorSignature;
	}

	// The poll's question, answered as the notifications would have: the
	// shape, the node map, the monitor - whichever changed without saying so.
	prPoll {
		var sig = this.prSignature;
		case
		{ sig != signature } { NdefRack.prModuleChanged(this, \structure) }
		{ this.prStateSignature != state } { NdefRack.prModuleChanged(this, \state) }
		{ this.prMonitorSignature != monitorState } { NdefRack.prModuleChanged(this, \monitor) };
	}

	prUpdateMonitor { monitorState = this.prMonitorSignature }

	// This runs inside whatever changed the Ndef - your Ndef(\x).set, your
	// new source. Nothing the rack does with the news may throw back into it.
	update { |who, what ... args|
		var change = switch(what,
			\set, \state, \unset, \state, \map, \state, \unmap, \state,
			\play, \monitor, \playN, \monitor, \stop, \monitor,
			// a source of its own, not a rebuild: NdefRack takes a width given in code
			\source, \source, \rebuild, \structure, \bus, \structure,
			\clear, \structure,
			// JITLibExtensions' addSpec announces itself this way, through Halo
			\spec, \structure
		);
		change !? {
			try { NdefRack.prModuleChanged(this, change) } { |err|
				("NdefRack: % - %".format(name, err.errorString)).warn
			}
		};
	}

	printOn { |stream| stream << this.class.name << "(" <<< name << ")" }
}
