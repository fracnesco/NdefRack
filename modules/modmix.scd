// modmix - four control signals summed, each with a gain of its own
//
// With voices, four inputs and an offset for each voice, summed voice by
// voice; the four gains stay one set for all.
(
	category: \utilities,
	help: "four control inputs, each times its gain (-1..1, an array of four), summed, plus offset; with voices, summed voice by voice, the gains shared",
	about: "Sums four modulation signals, each with its own gain, plus an offset. Use it to combine lfos and envelopes into one control, or to flip one upside down with a negative gain.",
	params: (
		in1: "the first modulation signal",
		in2: "the second modulation signal",
		in3: "the third modulation signal",
		in4: "the fourth modulation signal",
		gains: "how much of each input goes into the sum; negative turns it upside down",
		offset: "a fixed value added to the sum, moving it all up or down"
	),
	poly: { |n| {
		var ins = [\in1.kr(0 ! n, spec: [-1, 1]), \in2.kr(0 ! n, spec: [-1, 1]),
			\in3.kr(0 ! n, spec: [-1, 1]), \in4.kr(0 ! n, spec: [-1, 1])];
		(ins * \gains.kr(1 ! 4, spec: [-1, 1])).sum + \offset.kr(0 ! n, spec: [-1, 1])
	} }
)
