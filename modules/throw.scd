// throw - a send that is open only while its gate is
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// send with a gate on it: in is added to the bus, times level, only while
// open is on - the button, or a gate patched into it, on above 0.5 - opening
// in attack and closing in release. The dub throw: one snare into the
// delay's bus, a word into the reverb's. The output is in, untouched,
// whether the throw is open or not.
//
// The bus is picked as send's is, in the drop-down on the module's face or
// with NdefRack.make(\throw, bus: \delay); the rest is send's too - a mono
// cable feeds both sides of a 2-channel audio bus, it sends whether or not
// the module plays, no bus writes nowhere.
(
	category: \buses,
	help: "a send with a gate: in is added to a bus, times level, only while open is on (the button, or a gate), with attack and release; in is passed on untouched - pick the bus in the drop-down",
	about: "A send with a gate: the input reaches the bus only while open is on, fading in and out. The dub throw - one snare hit into the delay, one word into the reverb. The input passes on untouched either way.",
	params: (
		in: "the sound to throw; also leaves at the output untouched",
		level: "how much is added to the bus while open",
		open: "opens the send while on, above 0.5 - hold the button, or patch a gate",
		attack: "seconds the send takes to open",
		release: "seconds the send takes to close"
	),
	writes: true,
	bus: { |index, channels, rate| {
		var kr = rate == \control;
		var in = if (kr) { \in.kr(0 ! channels, spec: [-1, 1]) } { \in.ar(0 ! channels) };
		var level = \level.kr(1, spec: [0, 2, \amp]);
		var open = \open.kr(0, spec: ControlSpec(0, 1, \lin, 1, 0, "gate"));
		var gate = EnvGen.kr(Env.asr(
			\attack.kr(0.01, spec: [0.001, 2, \exp]), 1,
			\release.kr(0.05, spec: [0.001, 4, \exp])), open > 0.5);
		if (index.isNil) { in } {
			// its own fade: an Out here is outside the Ndef's envelope
			var env = EnvGate.new(i_level: 0, doneAction: 2, curve: if (kr) { \lin } { \sin },
				// deleted, the group goes exactly fadeTime later: a control bus
				// must be at 0 by then, or it keeps the last step
				fadeTime: if (kr) { (\fadeTime.kr(0.02) - (2 * ControlDur.ir)).max(0) });
			var sig = if (kr.not and: { channels == 2 }) {
				[in[0], Select.ar(Amplitude.kr(in[1], 0.01, 2) > 0.0001, [in[0], in[1]])]
			} { in };
			if (kr) { Out.kr(index, sig * level * gate * env) } { Out.ar(index, sig * level * gate * env) };
			in * env
		}
	} }
)
