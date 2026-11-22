// lfo8 - one lfo, eight times, spread around the cycle
//
// Eight channels of the same wave at eight phases. spread 1 puts them 45
// degrees apart, a whole turn between the first and the last; spread 0
// moves them as one. lo and hi are 0 and 1, so it goes straight into an
// array of levels - into verbos' levels it makes the partials take turns.
//
// With voices it is that many lfos of eight phases, every control a value
// per voice: the first phase of every voice, then the second of every
// voice, and so on - 8 times as many channels as voices.
(
	category: \modulation,
	help: "eight phases of one lfo (spread 1 = 45 degrees apart); shape morphs sine, triangle, saw, square; lo..hi, 0..1 to start; with voices, eight phases per voice, phase by phase",
	about: "One slow oscillator with eight outputs at eight points of its cycle. Patch it into an array of levels - such as verbos' levels - and the parts take turns; spread 0 moves all eight together. Outputs swing between lo and hi.",
	params: (
		rate: "how fast it cycles, in cycles per second (Hz)",
		reset: "fires to start the cycle again from 0 - patch a clock here to keep it in step",
		spread: "how far apart the eight phases are: 0 all together, 1 evenly around the cycle",
		shape: "the waveform: sine, triangle, saw, square - in between morphs between neighbours",
		pw: "the square's width: how much of each cycle it stays high; only heard near the square shape",
		lo: "the value at the bottom of the swing",
		hi: "the value at the top of the swing - set it below lo to turn the waves upside down"
	),
	outs: ["1", "2", "3", "4", "5", "6", "7", "8"],
	labels: (shape: ["sine", "triangle", "saw", "square"]),
	poly: { |n| {
		var rate = \rate.kr(0.2 ! n, spec: [0.001, 50, \exp]);
		var phase = Phasor.kr(\reset.tr(0 ! n), rate / ControlRate.ir, 0, 1);
		var spread = \spread.kr(1 ! n, spec: [0, 1]);
		var phases = (0..7).collect { |k| (phase + (k / 8 * spread)).wrap(0, 1) };
		var shape = \shape.kr(0 ! n, spec: [0, 3]);
		var pw = \pw.kr(0.5 ! n, spec: [0.01, 0.99]);
		var waves = phases.collect { |p|
			SelectX.kr(shape, [
				sin(p * 2pi),
				((p + 0.25).wrap(0, 1) * 4 - 1).fold2(1),
				(p + 0.5).wrap(0, 1) * 2 - 1,
				(p < pw) * 2 - 1
			])
		};
		var lo = \lo.kr(0 ! n, spec: [-1, 1]), hi = \hi.kr(1 ! n, spec: [-1, 1]);
		waves.collect { |w| w.linlin(-1, 1, lo, hi, nil) }.flat
	} }
)
