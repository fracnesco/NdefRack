// noise - white, pink and brown, one knob between them
//
// With voices, a noise for each - independent of the others - every
// control a value per voice.
(
	category: \oscillators,
	help: "white, pink and brown noise, morphed by color; with voices, an independent noise per voice",
	about: "A noise source, from bright white through pink to deep brown. Use it for wind, breath and hiss, as drum material through a filter, or as random modulation.",
	params: (
		color: "the noise: 0 white (bright), 1 pink, 2 brown (dark and rumbly) - in between blends them",
		amp: "the output level"
	),
	labels: (color: ["white", "pink", "brown"]),
	poly: { |n| {
		SelectX.ar(\color.kr(0 ! n, spec: [0, 2]),
			[WhiteNoise.ar(1 ! n), PinkNoise.ar(1 ! n), BrownNoise.ar(1 ! n)])
			* \amp.kr(0.2 ! n, spec: \amp)
	} }
)
