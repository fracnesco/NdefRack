// brightness - how bright the input sounds, as a signal
//
// The spectral centroid of in (SpecCentroid, on a 1024-point FFT): the
// balance point of its spectrum, in Hz, scaled from dark..bright to lo..hi,
// evenly in octaves. A sine reads its own frequency, a saw at 220 Hz about
// 4700 Hz, white noise half the sample rate. Quieter than floor dB, or
// silent, it holds its last value, so a pause does not move what it is
// patched into.
//
// With voices, each voice's own channel is read, with an FFT of its own,
// every control a value per voice.
(
	category: \listening,
	help: "the spectral centroid of in: dark..bright Hz scaled to lo..hi, in octaves; holds below floor dB; smooth; with voices, one per voice",
	about: "Listens to a sound and tells how bright it is - the balance point of its spectrum - as a signal from lo to hi. It holds its last value through silence, so a pause does not move what it controls. Patch it into a filter or any control to follow the timbre.",
	params: (
		in: "the sound to listen to",
		floor: "below this level in dB the sound counts as silent and the reading holds",
		smooth: "how slowly the reading follows changes, in seconds: 0 jumps at once",
		dark: "the brightness in Hz that reads as lo; anything darker reads lo too",
		bright: "the brightness in Hz that reads as hi; anything brighter reads hi too",
		lo: "the output for a dark sound",
		hi: "the output for a bright sound"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		// a buffer per voice: one LocalBuf would be one FFT shared by all
		var centroid = n.collect { |i| SpecCentroid.kr(FFT(LocalBuf(1024), in.asArray[i])) }.unbubble;
		// a silent frame reads 0 Hz, sooner than the level falls below floor
		var heard = (centroid > 0)
			* (Amplitude.kr(in, 0.01, 0.1).max(1e-5).ampdb > \floor.kr(-50 ! n, spec: [-90, 0]));
		Gate.kr(centroid, heard).lag(\smooth.kr(0.1 ! n, spec: [0, 2, 4]))
			.explin(\dark.kr(200 ! n, spec: \freq), \bright.kr(10000 ! n, spec: \freq),
				\lo.kr(0 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]))
	} }
)
