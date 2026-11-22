// blend - a crossfade between what is on a bus and in
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// mix fades the bus from what is on it (0, untouched) to in times level
// (1, replaced), channel for channel, linearly: the bus becomes bus * (1 -
// mix) + in * level * mix. The output is in, untouched. The bus is picked
// as send's is, in the drop-down on the module's face or with
// NdefRack.make(\blend, bus: \drums); no bus writes nowhere, and it writes
// whether or not the module plays.
//
// As with replace, the order the modules were made in matters: it fades
// what modules made before it have written in this block; what is made
// later, and every module playing onto the bus, lands on top at full level
// (measured 2026-11-20, 0.25 written, 0.5 in, mix 0.3: 0.325 with the
// writer made first, 0.4 with it made after). With nothing else on the bus
// it is in times level times mix.
(
	category: \buses,
	help: "a crossfade on a bus: mix from what is on it (0) to in times level (1) - only what modules made before it have written, what comes later lands on top; in is passed on - pick the bus in the drop-down",
	about: "Crossfades what is on a bus towards its own input: at mix 0 the bus is untouched, at 1 replaced by in times level. Only what modules made before it wrote is faded; later writers land on top. Pick the bus in the drop-down; in passes through unchanged.",
	params: (
		in: "the signal faded onto the bus; it is also passed on unchanged at the output",
		level: "how loud in is written onto the bus",
		mix: "the crossfade on the bus: 0 leaves it as it is, 1 replaces it with in times level"
	),
	writes: true,
	bus: { |index, channels, rate| {
		var kr = rate == \control;
		var in = if (kr) { \in.kr(0 ! channels, spec: [-1, 1]) } { \in.ar(0 ! channels) };
		var level = \level.kr(1, spec: [0, 2, \amp]);
		var mix = \mix.kr(0.5, spec: [0, 1]);
		if (index.isNil) { in } {
			// the module's own fade on the mix: it comes and goes smoothly
			var env = EnvGate.new(i_level: 0, doneAction: 2, curve: if (kr) { \lin } { \sin },
				// deleted, the group goes exactly fadeTime later: a control bus
				// must be at 0 by then, or it keeps the last step
				fadeTime: if (kr) { (\fadeTime.kr(0.02) - (2 * ControlDur.ir)).max(0) });
			if (kr) { XOut.kr(index, mix * env, in * level) } { XOut.ar(index, mix * env, in * level) };
			in * env
		}
	} }
)
