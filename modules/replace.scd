// replace - what is on a bus, replaced
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// in, times level, takes the place of what is on the bus, channel for
// channel; the output is in, untouched. The bus is picked as send's is, in
// the drop-down on the module's face or with NdefRack.make(\replace, bus:
// \drums); no bus writes nowhere, and it writes whether or not the module
// plays.
//
// The order the modules were made in matters here. It replaces what has
// been written to the bus so far in this block: by modules made before it.
// What is made later lands on top, untouched - so does every module playing
// onto that bus (the play button, Ndef.play(out:)), which goes to the end
// each time it is started (measured 2026-11-20: 0.25 written, 0.5 in - the
// bus has 0.5 with the writer made first, 0.75 with it made after). Make
// replace last, or delete it and make it again.
//
// It comes and goes - made, moved, deleted - in the Ndef's fadeTime,
// crossfading with the bus; made again on the same bus it dips a little in
// the middle of the fade (0.5 to 0.40 at 0.02 s, measured). With nothing
// patched in it replaces the bus with silence.
(
	category: \buses,
	help: "what is on a bus replaced by in times level - only what modules made before it have written, what comes later lands on top; with nothing at in it silences that; in is passed on - pick the bus in the drop-down",
	about: "Writes its input over what is already on a bus, so modules made before it on that bus are no longer heard there; modules made after it still add on top. Use it to take over a bus, or with nothing patched to silence it. Make it last.",
	params: (
		in: "the signal that takes the bus's place; passed on at the output untouched - nothing here replaces the bus with silence",
		level: "how loud in is written onto the bus; 1 as it comes, 0 silences the bus"
	),
	writes: true,
	bus: { |index, channels, rate| {
		var kr = rate == \control;
		var in = if (kr) { \in.kr(0 ! channels, spec: [-1, 1]) } { \in.ar(0 ! channels) };
		var level = \level.kr(1, spec: [0, 2, \amp]);
		if (index.isNil) { in } {
			// XOut on the module's own fade, not ReplaceOut: that one steps
			// when the module goes
			var env = EnvGate.new(i_level: 0, doneAction: 2, curve: if (kr) { \lin } { \sin },
				// deleted, the group goes exactly fadeTime later: a control bus
				// must be at 0 by then, or it keeps the last step
				fadeTime: if (kr) { (\fadeTime.kr(0.02) - (2 * ControlDur.ir)).max(0) });
			if (kr) { XOut.kr(index, env, in * level) } { XOut.ar(index, env, in * level) };
			in * env
		}
	} }
)
