// noisiness - how noisy the input sounds, as a signal
//
// The spectral flatness of in (SpecFlatness, on a 1024-point FFT), 0..1
// scaled to lo..hi: a sine reads about 0, a saw 0.25, pink noise 0.6, white
// noise 0.85. Quieter than floor dB, or silent, it holds its last value -
// silence itself would read 0.8, as noisy as noise.
//
// With voices, each voice's own channel is read, with an FFT of its own,
// every control a value per voice.
(
	category: \listening,
	help: "the spectral flatness of in, 0 (a sine) .. 1 (white noise is 0.85), scaled to lo..hi; holds below floor dB; smooth; with voices, one per voice",
	about: "Listens to a sound and puts out how noisy it is: near 0 for a pure tone, rising towards 1 for hiss. Patch it into a control so the character of what you play steers the patch. Holds its value through silence.",
	params: (
		in: "the sound to listen to",
		floor: "below this level, in dB, it stops measuring and holds the last value",
		smooth: "how slowly the reading follows, in seconds: higher is calmer",
		lo: "the output for a pure tone",
		hi: "the output for full noise"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		// a buffer per voice: one LocalBuf would be one FFT shared by all.
		// A silent frame has a centroid of 0 Hz: that is how it is told apart
		var spec = n.collect { |i|
			var chain = FFT(LocalBuf(1024), in.asArray[i]);
			[SpecCentroid.kr(chain), SpecFlatness.kr(chain)]
		}.flop.collect(_.unbubble);
		var heard = (spec[0] > 0)
			* (Amplitude.kr(in, 0.01, 0.1).max(1e-5).ampdb > \floor.kr(-50 ! n, spec: [-90, 0]));
		Gate.kr(spec[1], heard).lag(\smooth.kr(0.1 ! n, spec: [0, 2, 4]))
			.linlin(0, 1, \lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil)
	} }
)
