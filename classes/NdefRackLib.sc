// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackLib - the modules that come with the rack
//
// A library module is nothing but an Ndef source function. Each one is a
// plain file in modules/, so it can be read, copied and changed without
// recompiling anything:
//
//   Ndef(\osc, NdefRackLib(\verbos));     // the function itself
//   Ndef(\lfo, NdefRackLib(\lfo8));
//   NdefRack.make(\turing);               // the same, with a free name
//
//   NdefRackLib.keys;                     // everything there is
//   NdefRackLib.categories;               // [\effects, \filters, ...]
//   NdefRackLib.help(\verbos);            // one line on what it does
//   NdefRackLib.openFile(\verbos);        // the source, in the editor
//
// A module file holds one expression, an Event:
//
//   ( category: \filters, help: "what it does", func: { ... } )
//
// and its file name is its key. display: \scope or \spectrum gives the
// module a live display in the window - see NdefRackDisplay. A file whose module needs UGens that are
// not core says so on a line of its own, `// requires: DFM1` - the file is
// left out, and listed in NdefRackLib.skipped, when the class is missing,
// rather than failing to compile on somebody else's machine.
//
// A module that can play several voices gives poly: instead of func: - a
// function of the number of voices that answers the source function:
//
//   ( category: \filters, help: "...", poly: { |n| { RLPF.ar(\in.ar(0 ! n), \freq.kr(1200 ! n)) } } )
//
//   NdefRackLib(\lpf, 4);                 // the source for four voices
//   NdefRackLib.voicesFor(func);          // and back: 4
//
// Every control written `! n` has a value per voice. At one voice it is the
// plain module - `0 ! 1` is a single control. sliders: [\v] shows those
// per-voice controls as a slider per voice rather than one bar for all;
// follow: \in makes the module take as many voices as the cable patched
// into in brings (splay, sum).
//
// Two keys are only words for the window: outs: ["value", "gate"] names the
// output's channels, and labels: (scale: ["chromatic", "major", ...]) the
// stops of a selector, the first name for its lowest value - outsFor,
// labelsFor. Two more are words for people: about: "a few sentences on
// what the module does and how" and params: (rate: "cycles per second",
// shape: "...") - what each control does. The window's hint bar says them
// when the mouse is over the module or a control; NdefRackLib.describe(\lfo)
// puts all of it in one text, postHelp(\lfo) posts it - about, paramHelp.
// A third says how the window splits a module too long for one
// column into pages: pages: [tone: [\freq, \oct, \shape], rhythm: [\burst,
// \rest]] - a name and its controls per page, first page first - pagesFor.
//
// A module that reads a bus - one of BusManager's by name, or an index of
// your own - gives bus: instead - a function of the bus's index, channels
// and rate (nil, 1, \audio when there is no such bus) answering the source.
// The library keeps one function per bus as it is, so the function says
// which bus it reads:
//
//   NdefRackLib.busReader(\bus, \reverb);   // the source reading \reverb now
//   NdefRackLib.busReader(\bus, 16, 2);     // ... audio buses 16 and 17
//   NdefRackLib.busFor(func);               // and back: (name: \reverb, index: 16, ...)
//
// A module that writes to its bus - send, throw, replace - is made the same
// way and says writes: true, so the window and the warnings say "writes to"
// where they say "reads"; one that does both, insert, adds reads: true.
// insert says writes: \audio - on a control bus it only reads:
//
//   NdefRackLib.writesBus(\send);           // -> true
//   NdefRackLib.busVerb(\insert);           // -> "reads and writes"
//   NdefRackLib.busVerb(\insert, \control); // -> "reads"
//
// A module that places a sound among speakers - vbap, ambi - gives
// speakers: instead - a function of a speaker setup (an NdefRackSpeakers)
// answering the source. The library keeps one function per setup, so the
// function says which speakers it plays to:
//
//   NdefRackLib.speakerFunc(\vbap, \octo);   // the source for eight in a ring
//   NdefRackLib.speakersFor(func);           // and back: (name: \octo, setup: NdefRackSpeakers(\octo))
//
// Your own modules: files of the same shape in modules-local/ (or a folder
// in it) beside modules/ - gitignored, loaded with the rest -, in a folder
// of your own with NdefRackLib.addFolder("~/my-modules"), or
// NdefRackLib.add(\key, func, \category, "help").
// =====================================================================

NdefRackLib {
	classvar <entries;			// key -> (key, func, poly, category, help, path, ...)
	classvar <skipped;			// key -> why the file was left out
	classvar <folders;			// folders added with addFolder, read after modules/
	classvar funcKeys;			// function -> key, never emptied: see keyFor
	classvar funcVoices;		// function -> voices, for those a poly: made
	classvar funcBuses;			// function -> (name, index, channels, rate), for those a bus: made
	classvar funcSpeakers;		// function -> (name, setup), for those a speakers: made
	classvar shapes;			// function -> (rate:, numChannels:, controls:), built once
	classvar funcCode;			// function -> its source as code that runs without the rack
	classvar <>maxVoices = 16;
	classvar loaded = false;

	*initClass {
		entries = IdentityDictionary.new;
		skipped = IdentityDictionary.new;
		funcKeys = IdentityDictionary.new;
		funcVoices = IdentityDictionary.new;
		funcBuses = IdentityDictionary.new;
		funcSpeakers = IdentityDictionary.new;
		shapes = IdentityDictionary.new;
		funcCode = IdentityDictionary.new;
		folders = [];
	}

	// NdefRackLib(\verbos) is the function, ready for Ndef(\name, ...);
	// NdefRackLib(\wave, 4) the one for four voices
	*new { |key, voices| ^this.at(key, voices) }

	// One function per number of voices, made the first time it is asked for
	// and kept: the same function every time is what lets voicesFor and keyFor
	// read a module back from its Ndef.
	*at { |key, voices|
		var entry = this.entry(key);
		var n = (voices ? 1).asInteger;
		if (entry.isNil) {
			("NdefRackLib: there is no module called %".format(key.asCompileString)).warn;
			^nil
		};
		if (n <= 1) { ^entry[\func] };
		if (entry[\poly].isNil) {
			if (entry[\voiceMaker] == true) { ^this.withVoices(entry[\func], n) };
			("NdefRackLib: % is one voice only - it has no poly:".format(key.asCompileString)).warn;
			^entry[\func]
		};
		n = n.min(maxVoices);
		^entry[\funcs].at(n) ?? { this.prVoiceFunc(entry, n) }
	}

	// A library function as code that makes it again in plain SuperCollider,
	// without the rack: a func:'s own text, or a maker's - poly:, bus:,
	// speakers: - applied to what made this function: { |n| { ... } }.value(16),
	// a speaker setup as its numbers. nil when the text is not there (a
	// function made in a class file).
	*codeFor { |func| ^func !? { funcCode[func] } }

	// The same module for n voices: a library function's sibling - on the
	// same bus, the same speakers - or the function itself when the module
	// has no voices.
	*withVoices { |func, n|
		var key = this.keyFor(func), entry = key !? { this.entry(key) };
		var b, sp;
		n = (n ? 1).asInteger.clip(1, maxVoices);
		if (entry.isNil or: { entry[\voiceMaker] != true }) { ^func };
		b = this.busFor(func);
		sp = this.speakersFor(func);
		^case
		{ b.notNil } { this.busReader(key, b[\name] ?? { b[\index] }, b[\channels], b[\rate], n) }
		{ sp.notNil } { this.speakerFunc(key, sp[\name], n) }
		{ this.at(key, n) }
	}

	// what a function's module is - (rate:, numChannels:, controls:) - built
	// in the language, once per function
	*shapeOf { |func|
		^func !? {
			shapes.at(func) ?? {
				this.prShape(func) !? { |s| shapes.put(func, s); s }
			}
		}
	}

	// whether a module can play several voices
	*canVoice { |key| ^(this.entry(key) !? { |e| e[\voiceMaker] }) == true }

	// how many voices a library function plays: nil for anything else
	*voicesFor { |func| ^func !? { funcVoices[func] } }

	// The controls that have a value per voice: those that come out two wide
	// when the module is built for two voices.
	*voiceKeys { |key|
		var entry = this.entry(key);
		if (entry.isNil or: { entry[\voiceMaker] != true }) { ^[] };
		^entry[\voiceKeys] ?? {
			var keys = this.shapeOf(this.withVoices(entry[\func], 2)) !? { |s|
				s[\controls].select { |cn| cn.defaultValue.asArray.size == 2 }.collect(_.name)
			} ? [];
			entry[\voiceKeys] = keys;
			keys
		}
	}

	// the per-voice controls shown as a slider per voice
	*sliders { |key| ^this.entry(key) !? { |e| e[\sliders] } ? [] }

	// the input whose cable sets the number of voices, or nil
	*follow { |key| ^this.entry(key) !? { |e| e[\follow] } }

	// ------------------------------------------------------------- names

	// What each output channel is, when the module says - outs: ["value",
	// "gate"] - as Strings, first channel first; nil when it does not. Only
	// words for the window and its hints: a cable is still the whole output.
	*outsFor { |key| ^this.entry(key) !? { |e| e[\outs] } }

	// What the stops of a selector are called - labels: (scale: ["chromatic",
	// "major", ...]), the first name for the control's lowest value, the next
	// for one more - as an IdentityDictionary of name -> Strings; with a
	// control's name, that control's names or nil.
	*labelsFor { |key, name|
		var labels = this.entry(key) !? { |e| e[\labels] };
		^if (name.isNil) { labels } { labels !? { labels[name.asSymbol] } }
	}

	// How the window pages a module too long for one column - pages: [tone:
	// [\freq, \oct], rhythm: [\burst, \rest]] - as an Array of (name:
	// String, names: [Symbols]), first page first; nil when the module does
	// not say, and the window cuts the bands evenly.
	*pagesFor { |key| ^this.entry(key) !? { |e| e[\pages] } }

	// ------------------------------------------------------------------ buses

	// whether a module is on a bus - reads it, or writes to it: one of
	// BusManager's by name, or an index
	*canBus { |key| ^(this.entry(key) !? { |e| e[\bus].notNil }) == true }

	// whether a bus module writes to its bus (writes: true) - send, replace -
	// and whether it reads it: bus does, a writer only when it says reads: true.
	// writes: \audio writes to audio buses only - insert, a plain reader on a
	// control bus; given the bus's rate, writesBus answers for that rate,
	// without one whether it ever writes.
	*writesBus { |key, rate|
		var w = this.entry(key) !? { |e| e[\writes] };
		^case
		{ w == true } { true }
		{ w == \audio } { [\control, \kr].includes(rate).not }
		{ false }
	}
	*readsBus { |key| ^(this.entry(key) !? { |e| e[\bus].notNil and: { e[\reads] } }) == true }

	// what it does with its bus, for a sentence: "reads", "writes to",
	// "reads and writes" - on a bus of that rate, when one is given
	*busVerb { |key, rate|
		^case
		{ this.writesBus(key, rate).not } { "reads" }
		{ this.readsBus(key) } { "reads and writes" }
		{ "writes to" }
	}

	// The source that reads a bus - or writes to it - as it is now: its index, channels and rate
	// are in the function, made the first time they are asked for and kept,
	// so busFor reads them back from an Ndef. bus is one of BusManager's
	// names - one it does not know, or BusManager not being there, is the
	// module's silent stand-in, still bound to the name - or a bus index, at
	// channels and rate (1, \audio), or a Bus; nil, no bus.
	*busReader { |key, bus, channels, rate, voices = 1|
		var entry = this.entry(key);
		var bm = \BusManager.asClass;
		var name, found, index, tag;
		if (entry.isNil) {
			("NdefRackLib: there is no module called %".format(key.asCompileString)).warn;
			^nil
		};
		if (entry[\bus].isNil) {
			("NdefRackLib: % is not a bus module - it has no bus:".format(key.asCompileString)).warn;
			^nil
		};
		case
		{ bus.isKindOf(Bus) } {
			index = bus.index;
			channels = bus.numChannels;
			rate = bus.rate;
		}
		{ bus.isNumber } { index = bus.asInteger }
		{ bus.notNil } {
			// BusManager's width and rate, not the ones given
			name = bus.asSymbol;
			found = bm !? { bm.at(name) };
			channels = found !? { found.numChannels };
			rate = found !? { found.rate };
			index = found !? { found.index };
		};
		if (index.notNil and: { index < 0 }) {
			("NdefRackLib: % is not a bus index".format(index)).warn;
			^nil
		};
		// no bus is the one silent stand-in, however wide it was asked for
		if (index.isNil) { channels = nil; rate = nil };
		channels = (channels ? 1).asInteger.clip(1, 128);
		rate = if ([\control, \kr].includes(rate)) { \control } { \audio };
		voices = if (entry[\voiceMaker] == true) { (voices ? 1).asInteger.clip(1, maxVoices) } { 1 };
		tag = [name, index, channels, rate, voices];
		^entry[\busFuncs].at(tag) ?? { this.prBusFunc(entry, tag) }
	}

	// which bus a library function reads or writes - (name:, index:, channels:, rate:),
	// name nil for a bus index of your own, name and index nil for none - or
	// nil for any other function
	*busFor { |func| ^func !? { funcBuses[func] } }

	// --------------------------------------------------------------- speakers

	// whether a module plays to a speaker setup: vbap, ambi
	*canSpeakers { |key| ^(this.entry(key) !? { |e| e[\speakers].notNil }) == true }

	// The source that plays to a speaker setup, made the first time it is
	// asked for and kept, so speakersFor reads the setup back from an Ndef.
	// setup is a name from NdefRackSpeakers' table, a setup itself, or nil for
	// the default. A name the table does not have is the default's speakers
	// in a function of its own, still bound to the name: the module follows
	// when the name is added.
	*speakerFunc { |key, setup, voices = 1|
		var entry = this.entry(key);
		var name, obj, tag;
		if (entry.isNil) {
			("NdefRackLib: there is no module called %".format(key.asCompileString)).warn;
			^nil
		};
		if (entry[\speakers].isNil) {
			("NdefRackLib: % does not play to speakers - it has no speakers:".format(key.asCompileString)).warn;
			^nil
		};
		case
		{ setup.isKindOf(NdefRackSpeakers) } { obj = setup; name = setup.name }
		{ setup.isNil } { obj = NdefRackSpeakers.for(nil); name = obj.name }
		{ setup.isKindOf(Symbol) or: { setup.isKindOf(String) } } {
			name = setup.asSymbol;
			obj = NdefRackSpeakers.at(name) ?? { NdefRackSpeakers.for(nil) };
		}
		{
			("NdefRackLib: % is not a speaker setup - a name from NdefRackSpeakers.names"
				.format(setup)).warn;
			^nil
		};
		voices = if (entry[\voiceMaker] == true) { (voices ? 1).asInteger.clip(1, maxVoices) } { 1 };
		tag = [name, obj, voices];
		^entry[\speakerFuncs].at(tag) ?? { this.prSpeakerFunc(entry, tag) }
	}

	// which speakers a library function plays to - (name:, setup:), the setup
	// being the default's when the name was not in the table - or nil for any
	// other function
	*speakersFor { |func| ^func !? { funcSpeakers[func] } }

	// What the module is for that many voices - (rate:, numChannels:,
	// controls:) - built in the language alone, no server, once per function.
	*shape { |key, voices|
		var func = this.at(key, voices);
		^func !? {
			shapes.at(func) ?? {
				this.prShape(func) !? { |s| shapes.put(func, s); s }
			}
		}
	}

	*entry { |key|
		this.prEnsureLoaded;
		^entries[key.asSymbol]
	}

	*includes { |key| ^this.entry(key).notNil }

	*keys {
		this.prEnsureLoaded;
		^entries.keys.asArray.sort
	}

	*categories {
		this.prEnsureLoaded;
		^entries.values.collect(_[\category]).asSet.asArray.sort
	}

	*keysIn { |category|
		category = category.asSymbol;
		^this.keys.select { |k| entries[k][\category] == category }
	}

	*help { |key| ^this.entry(key) !? (_[\help]) }

	// A few sentences on what the module does - the module's about:, or its
	// one-line help when it has none.
	*about { |key|
		^this.entry(key) !? { |e| if (e[\about].isNil) { e[\help] } { e[\about] } }
	}

	// What a control does, in words - the module's params: entry for it - or
	// nil; without a name, every control's as an IdentityDictionary.
	*paramHelp { |key, name|
		var d = this.entry(key) !? { |e| e[\params] };
		^if (name.isNil) { d } { d !? { d[name.asSymbol] } }
	}

	// Everything about a module in one text: what it does, then each control -
	// its range and default, and what it does. Built without a server
	// (shape): at one voice, and at two for the controls only voices have.
	*describe { |key|
		var entry = this.entry(key);
		var shape, lines, words, mono, voiceKeys;
		if (entry.isNil) { ^nil };
		words = entry[\params] ? IdentityDictionary.new;
		mono = this.shape(key, 1);
		voiceKeys = this.voiceKeys(key);
		shape = if (this.canVoice(key)) { this.shape(key, 2) ? mono } { mono };
		lines = [
			"% - % (%)".format(key, entry[\help], entry[\category]),
			"",
			this.about(key)
		];
		this.outsFor(key) !? { |o| lines = lines ++ ["", "output: " ++ o.collect { |n, i| "% on %".format(n, i + 1) }.join(", ")] };
		shape !? {
			lines = lines.add("");
			shape[\controls].do { |cn|
				var spec = shape[\specs] !? { |sp| sp[cn.name] } !? (_.asSpec);
				var desc = words[cn.name] ? "";
				var def = cn.defaultValue;
				var voice = voiceKeys.includes(cn.name);
				var what;
				var onlyVoices = mono.notNil and: { mono[\controls].any { |c| c.name == cn.name }.not };
				if (voice) { def = def.asArray.first };
				what = case
				{ cn.rate == \audio } { "audio input" }
				{ cn.rate == \trigger } { "trigger" }
				{
					"default %%".format(if (def.isNumber) { def.round(0.0001) } { def },
						spec !? { ", % to %".format(spec.minval.round(0.0001), spec.maxval.round(0.0001)) } ? "")
				};
				if (onlyVoices) { what = what ++ ", only with voices" };
				lines = lines.add("  % (%)%".format(cn.name, what,
					if (desc.isEmpty) { "" } { ": " ++ desc }));
				this.labelsFor(key, cn.name) !? { |l| lines = lines.add("      stops: " ++ l.join(", ")) };
			};
		};
		^lines.join("\n")
	}

	*postHelp { |key| (this.describe(key) ?? { "NdefRackLib: there is no module called %".format(key.asCompileString) }).postln }

	// Which library module a function came from, or nil. It is a lookup by
	// identity, and the table is never emptied, so an Ndef made before a
	// reload still says what it is.
	*keyFor { |func| ^func !? { funcKeys[func] } }

	// --------------------------------------------------------------- loading

	// next to the class files, so the modules travel with the repository
	// however it was installed
	*dir { ^this.filenameSymbol.asString.dirname.dirname +/+ "modules" }

	// modules of your own kept out of git: modules-local/ beside modules/,
	// and each folder in it (modules-local/feedback/), read after modules/
	*localDirs {
		var root = this.dir.dirname +/+ "modules-local";
		^[root] ++ (root +/+ "*").pathMatch.select { |p| p.last == $/ }
	}

	*load {
		entries = IdentityDictionary.new;
		skipped = IdentityDictionary.new;
		loaded = true;
		([this.dir] ++ this.localDirs ++ folders).do { |dir| this.prLoadFolder(dir) };
		this.changed(\library);
		^this
	}

	// after editing a module file: Ndefs already running keep their old
	// function until you set it again
	*reload { ^this.load }

	*addFolder { |path|
		path = path.standardizePath;
		if (folders.includes(path).not) { folders = folders.add(path) };
		this.prEnsureLoaded;
		this.prLoadFolder(path);
		this.changed(\library);
		^this
	}

	// poly: a function of the number of voices answering the source, in place
	// of func - see the top of this file; sliders and follow go with it. bus:
	// a function of a bus's index, channels and rate, for a module reading one
	// of BusManager's buses - or writing to it: writes: true, or \audio for
	// audio buses only (and reads: true when it does both).
	// speakers: a function of a speaker setup, for a module panning among
	// its speakers. outs: and labels: name the output's channels and a
	// selector's stops, pages: groups the controls into pages - see outsFor,
	// labelsFor and pagesFor.
	*add { |key, func, category, help, path, display, poly, sliders, follow, bus, writes, reads, speakers,
		outs, labels, pages, about, params|
		var entry;
		key = key.asSymbol;
		this.prEnsureLoaded;
		entry = this.prEntry(key, func, poly,
			(category: category, help: help, display: display, sliders: sliders, follow: follow, bus: bus,
				writes: writes, reads: reads, speakers: speakers, outs: outs, labels: labels, pages: pages,
				about: about, params: params), path);
		if (entry.isNil) {
			("NdefRackLib: % needs a function, or a poly:, a bus: or a speakers: that makes one - got %"
				.format(key.asCompileString, (poly ? bus ? speakers ? func).class)).warn;
			^nil
		};
		entries[key] = entry;
		skipped.removeAt(key);
		this.changed(\library);
		^entry[\func]
	}

	*remove { |key|
		var entry = entries.removeAt(key.asSymbol);
		entry !? { this.changed(\library) };
		^entry
	}

	*openFile { |key|
		var path = this.entry(key) !? (_[\path]);
		if (path.isNil) {
			("NdefRackLib: % has no file".format(key.asCompileString)).warn;
			^nil
		};
		// Document exists only inside the IDE: looked up by name, so the class
		// library still compiles in a headless sclang
		^\Document.asClass !? { |doc| doc.open(path) } ?? { path.openOS }
	}

	// ----------------------------------------------------------------- private

	*prEnsureLoaded { if (loaded.not) { this.load } }

	// a list of names as Strings - from Strings or Symbols - or nil for
	// anything else, or none
	*prNames { |names|
		if (names.isNil or: { names.isString } or: { names.isKindOf(Symbol) }
			or: { names.isKindOf(SequenceableCollection).not }) { ^nil };
		names = names.collect(_.asString);
		^if (names.isEmpty) { nil } { names.asArray }
	}

	// pages: [name: [controls], ...] as (name:, names:) Events - a malformed
	// page is left out; nil for none
	*prPageList { |pages|
		var out = [];
		if (pages.isKindOf(SequenceableCollection).not or: { pages.isString }) { ^nil };
		pages.pairsDo { |name, names|
			if ((name.isString or: { name.isKindOf(Symbol) })
				and: { names.isKindOf(SequenceableCollection) } and: { names.isString.not }) {
				names = names.select { |n| n.isString or: { n.isKindOf(Symbol) } }.collect(_.asSymbol);
				if (names.notEmpty) { out = out.add((name: name.asString, names: names.asArray)) }
			}
		};
		^if (out.isEmpty) { nil } { out }
	}

	*prLoadFolder { |dir|
		(dir.standardizePath +/+ "*.scd").pathMatch.sort.do { |path|
			this.prLoadFile(path)
		};
	}

	*prLoadFile { |path|
		var key = PathName(path).fileNameWithoutExtension.asSymbol;
		var missing = this.prMissingClasses(path);
		var entry;
		if (missing.notEmpty) {
			skipped[key] = "needs %".format(missing.join(", "));
			^nil
		};
		// a module that does not compile posts its own error; the rest load
		entry = try {
			path.load !? { |e| if (e.isKindOf(Event)) { this.prEntry(key, e[\func], e[\poly], e, path) } }
		} { |err|
			skipped[key] = err.errorString;
			nil
		};
		if (entry.isNil) {
			skipped[key] = skipped[key] ? "not a module - one Event with a func, a poly, a bus or a speakers";
			^nil
		};
		entries[key] = entry;
		^entry
	}

	// The library's record of a module, or nil when there is no function to
	// make it from. A poly: is asked for one voice straight away: that is the
	// module's func, the one NdefRackLib(\key) answers. A bus: is asked for no
	// bus at all - the silent stand-in, which writes nowhere, bound to no name -
	// for the same reason, and a speakers: for the default setup.
	*prEntry { |key, func, poly, info, path|
		var entry;
		var bus = info[\bus];
		var speakers = info[\speakers];
		var setup;
		if (poly.isKindOf(Function).not) { poly = nil };
		if (bus.isKindOf(Function).not) { bus = nil };
		if (speakers.isKindOf(Function).not or: { bus.notNil }) { speakers = nil };
		poly !? { func = poly.value(1) };
		speakers !? {
			setup = NdefRackSpeakers.for(nil);
			func = speakers.value(setup.data, 1);
			poly = nil;
		};
		bus !? { func = bus.value(nil, 1, \audio, 1) };
		if (func.isKindOf(Function).not) { ^nil };
		entry = (
			key: key,
			func: func,
			poly: poly,
			funcs: IdentityDictionary[1 -> func],
			category: (info[\category] ? \other).asSymbol,
			help: info[\help] ? "",
			path: path,
			display: info[\display] !? (_.asSymbol),
			sliders: (info[\sliders] ? []).asArray.collect(_.asSymbol),
			follow: info[\follow] !? (_.asSymbol),
			outs: this.prNames(info[\outs]),
			labels: info[\labels] !? { |l|
				var d = IdentityDictionary.new;
				if (l.respondsTo(\keysValuesDo)) {
					l.keysValuesDo { |name, names| this.prNames(names) !? { |n| d[name.asSymbol] = n } }
				};
				if (d.isEmpty) { nil } { d }
			},
			pages: this.prPageList(info[\pages]),
			about: info[\about] !? { |a| if (a.isString and: { a.notEmpty }) { a } },
			params: info[\params] !? { |l|
				var d = IdentityDictionary.new;
				if (l.respondsTo(\keysValuesDo)) {
					l.keysValuesDo { |name, text| if (text.isString) { d[name.asSymbol] = text } }
				};
				if (d.isEmpty) { nil } { d }
			},
			bus: bus,
			busFuncs: Dictionary.new,
			speakers: speakers,
			speakerFuncs: Dictionary.new,
			// a bus module reads its bus unless it says it writes - true, or
			// \audio for audio buses only; one that does both says both
			writes: if (bus.notNil and: { [true, \audio].includes(info[\writes]) }) { info[\writes] } { false },
			reads: bus.notNil and: { [true, \audio].includes(info[\writes]).not or: { info[\reads] == true } },
			// voices: a poly:, or a bus: or speakers: maker taking the number
			// of voices after what it takes for its bus or setup
			voiceMaker: poly.notNil
				or: { bus.notNil and: { bus.def.argNames.size >= 4 } }
				or: { speakers.notNil and: { speakers.def.argNames.size >= 2 } }
		);
		funcKeys[func] = key;
		funcCode[func] = case
			{ poly.notNil } { this.prMakerCode(poly, [1]) }
			{ speakers.notNil } { this.prMakerCode(speakers, [setup.data, 1]) }
			{ bus.notNil } { this.prMakerCode(bus, [nil, 1, \audio, 1]) }
			{ func.def.sourceCode };
		if (entry[\voiceMaker]) { funcVoices[func] = 1 };
		bus !? {
			entry[\busFuncs].put([nil, nil, 1, \audio, 1], func);
			funcBuses[func] = (name: nil, index: nil, channels: 1, rate: \audio);
		};
		speakers !? {
			entry[\speakerFuncs].put([setup.name, setup, 1], func);
			funcSpeakers[func] = (name: setup.name, setup: setup);
		};
		^entry
	}

	// tag: [name, setup, voices]; the maker gets the setup's numbers
	// (NdefRackSpeakers:data), so the function it answers needs no class
	*prSpeakerFunc { |entry, tag|
		var func = entry[\speakers].value(tag[1].data, tag[2]);
		if (func.isKindOf(Function).not) {
			("NdefRackLib: the speakers: of % gave no function for %"
				.format(entry[\key].asCompileString, tag[0])).warn;
			^entry[\func]
		};
		entry[\speakerFuncs].put(tag, func);
		funcKeys[func] = entry[\key];
		funcCode[func] = this.prMakerCode(entry[\speakers], [tag[1].data, tag[2]]);
		funcSpeakers[func] = (name: tag[0], setup: tag[1]);
		if (entry[\voiceMaker]) { funcVoices[func] = tag[2] };
		^func
	}

	// tag: [name, index, channels, rate, voices]
	*prBusFunc { |entry, tag|
		var func = entry[\bus].value(tag[1], tag[2], tag[3], tag[4]);
		if (func.isKindOf(Function).not) {
			("NdefRackLib: the bus: of % gave no function for %"
				.format(entry[\key].asCompileString, tag[0])).warn;
			^entry[\func]
		};
		entry[\busFuncs].put(tag, func);
		funcKeys[func] = entry[\key];
		funcCode[func] = this.prMakerCode(entry[\bus], tag[1..4]);
		funcBuses[func] = (name: tag[0], index: tag[1], channels: tag[2], rate: tag[3]);
		if (entry[\voiceMaker]) { funcVoices[func] = tag[4] };
		^func
	}

	*prVoiceFunc { |entry, n|
		var func = entry[\poly].value(n);
		if (func.isKindOf(Function).not) {
			("NdefRackLib: the poly: of % gave no function for % voices"
				.format(entry[\key].asCompileString, n)).warn;
			^entry[\func]
		};
		entry[\funcs].put(n, func);
		funcKeys[func] = entry[\key];
		funcCode[func] = this.prMakerCode(entry[\poly], [n]);
		funcVoices[func] = n;
		^func
	}

	// maker's text.value(args): what made the function, as code - numbers,
	// a speaker setup's numbers as an Event. Only as many arguments as the
	// maker takes, so one written without voices gets none.
	*prMakerCode { |maker, args|
		var text = maker.def.sourceCode;
		^text !? {
			"%.value(%)".format(text, args.keep(maker.def.argNames.size).collect(_.asCompileString).join(", "))
		}
	}

	// Built as a SynthDef that is never sent: what its output is and which
	// controls it has. A module that cannot be built says so, once.
	*prShape { |func|
		var out, def, flat;
		^try {
			def = SynthDef(\ndefRackShape, { out = SynthDef.wrap(func) });
			flat = out.asArray.flat;
			(
				rate: if (flat.any { |x| x.rate == \audio }) { \audio } { \control },
				numChannels: flat.size,
				controls: def.allControlNames,
				// the spec: given with each control, name -> ControlSpec
				specs: def.tryPerform(\specs)
			)
		} { |err|
			("NdefRackLib: % does not build - %".format(funcKeys[func], err.errorString)).warn;
			nil
		}
	}

	// "// requires: DFM1, MoogLadder" names classes a file cannot compile without
	*prMissingClasses { |path|
		var text = File.readAllString(path);
		var line = text.split($\n).detect { |l| l.beginsWith("// requires:") };
		if (line.isNil) { ^[] };
		^line.drop("// requires:".size).split($,)
			.collect(_.stripWhiteSpace)
			.reject(_.isEmpty)
			.select { |name| name.asSymbol.asClass.isNil }
	}
}
