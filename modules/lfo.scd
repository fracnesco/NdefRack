// lfo - a low frequency oscillator: sine, triangle, saw, square, random
//
// Every shape starts at 0 going up, in step with the sine, so morphing
// between them does not jump. The random shape holds a new value for each
// cycle. reset starts the cycle again. With voices it is that many lfos,
// and the random shape draws its own for each; spread, there only with more
// than one voice, sets them apart around the cycle - 1 spaces them evenly.
(
	category: \modulation,
	help: "sine, triangle, saw, square and stepped random, morphed by shape, scaled to lo..hi; reset restarts the cycle; with voices, spread sets them apart",
	about: "A slow oscillator for moving other controls. shape morphs smoothly from sine to triangle, saw, square and a stepped random value per cycle; the output swings between lo and hi. With voices, each is its own lfo and spread staggers them around the cycle.",
	params: (
		rate: "how fast it cycles, in cycles per second (Hz): 1 is once a second",
		reset: "fires to start the cycle again from 0 - patch a clock here to keep it in step",
		spread: "with voices: how far apart around the cycle they start; 0 together, 1 evenly spaced",
		shape: "the waveform: sine, triangle, saw, square, random - in between morphs between neighbours",
		pw: "the square's width: how much of each cycle it stays high; only heard near the square shape",
		lo: "the value at the bottom of the swing",
		hi: "the value at the top of the swing - set it below lo to turn the wave upside down"
	),
	labels: (shape: ["sine", "triangle", "saw", "square", "random"]),
	poly: { |n| {
		var rate = \rate.kr(1 ! n, spec: [0.001, 100, \exp]);
		var phase = Phasor.kr(\reset.tr(0 ! n), rate / ControlRate.ir, 0, 1);
		var wrapped;
		if (n > 1) { phase = (phase + ((0 .. n - 1) / n * \spread.kr(0, spec: [0, 1]))).wrap(0, 1) };
		wrapped = phase < Delay1.kr(phase);
		SelectX.kr(\shape.kr(0 ! n, spec: [0, 4]), [
			sin(phase * 2pi),
			((phase + 0.25).wrap(0, 1) * 4 - 1).fold2(1),
			(phase + 0.5).wrap(0, 1) * 2 - 1,
			(phase < \pw.kr(0.5 ! n, spec: [0.01, 0.99])) * 2 - 1,
			Latch.kr(WhiteNoise.kr(1 ! n), wrapped + Impulse.kr(0))
		]).linlin(-1, 1, \lo.kr(-1 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil)
	} }
)
