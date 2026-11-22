// adsr - attack, decay, sustain while the gate is held, release after
//
// With voices, an envelope for each: a gate per voice patched into trig
// opens each one on its own; the button opens them all.
(
	category: \modulation,
	help: "an ADSR envelope, 0..1 scaled to lo..hi: hold trig (the button, or a gate patched in) to open it",
	about: "An envelope that rises while a gate is held and falls away when it is let go. Patch a gate or a sequencer into trig and the output into an amp's gain or a filter's oct to shape each note.",
	params: (
		attack: "how long it takes to rise from lo to hi when the gate opens, in seconds",
		decay: "how long it then takes to fall to the sustain level, in seconds",
		sustain: "the level it holds while the gate stays open, as a share of the way from lo to hi",
		release: "how long it takes to fall back to lo after the gate closes, in seconds",
		trig: "the gate: open above 0.5 - hold the button, or patch a gate or clock here",
		lo: "the output when the envelope is closed",
		hi: "the output at the top of the attack - set it below lo to turn the envelope upside down"
	),
	poly: { |n| {
		var env = EnvGen.kr(
			Env.adsr(
				\attack.kr(0.01 ! n, spec: [0.001, 10, \exp]),
				\decay.kr(0.3 ! n, spec: [0.001, 10, \exp]),
				\sustain.kr(0.5 ! n, spec: [0, 1]),
				\release.kr(0.5 ! n, spec: [0.001, 10, \exp])
			),
			\trig.kr(0 ! n) > 0.5
		);
		env.linlin(0, 1, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil)
	} }
)
