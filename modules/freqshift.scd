// freqshift - every partial moved by the same number of hertz
//
// Not a pitch shift: harmonics stop being harmonic. A few hertz is a slow
// swirl, a few hundred a metallic other thing. With voices, a shifter for
// each, every control a value per voice.
(
	category: \effects,
	help: "a frequency shifter: every partial moved by shift Hz, up or down; mix; with voices, one per voice",
	about: "A frequency shifter: every partial moves by the same number of hertz, so harmonics stop being harmonic. A few hertz gives a slow swirl, a few hundred a metallic, bell-like sound.",
	params: (
		in: "the sound to shift",
		shift: "how far every partial moves, in Hz: positive up, negative down",
		mix: "0 dry, 1 all shifted"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		XFade2.ar(in, FreqShift.ar(in, \shift.kr(0 ! n, spec: [-1000, 1000])),
			\mix.kr(1 ! n, spec: [0, 1]) * 2 - 1)
	} }
)
