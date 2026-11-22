// fold - a wavefolder: past the edge, the wave turns back
//
// With voices, a folder for each, every control a value per voice.
(
	category: \effects,
	help: "a wavefolder: gain drives the input past +-1, where it folds back; bias makes the folds lopsided; with voices, one per voice",
	about: "A wavefolder: the sound driven past the edge turns back on itself, adding bright, buzzy harmonics that move as the gain changes. Feed it a sine or triangle and patch an envelope or lfo into gain.",
	params: (
		in: "the sound to fold - smooth waves fold best",
		gain: "how hard the input is driven past the edge: more gives more folds and a brighter sound",
		bias: "shifts the wave before folding, making the folds lopsided and adding even harmonics",
		level: "the output level"
	),
	poly: { |n| {
		var sig = (\in.ar(0 ! n) * \gain.kr(2 ! n, spec: [1, 20, \exp])) + \bias.kr(0 ! n, spec: [-1, 1]);
		LeakDC.ar(sig.fold2(1)) * \level.kr(0.5 ! n, spec: [0, 1])
	} }
)
