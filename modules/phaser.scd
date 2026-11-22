// phaser - four allpass stages swept by an lfo of their own
//
// The stages shift the phase without touching the level; summed with the
// dry signal they cut notches, and the lfo sweeps the notches around freq,
// depth octaves either way. fb deepens them. oct moves the centre too.
// With voices, a phaser for each, its own lfo too, every control a value
// per voice.
(
	category: \effects,
	help: "a four-stage phaser: its own lfo at rate sweeps the notches around freq by depth octaves; oct, fb, mix; with voices, one per voice",
	about: "A four-stage phaser: moving notches sweep through the sound for a swirling, jet-like shimmer. Its own lfo does the sweeping; fb makes it deeper and more pronounced.",
	params: (
		in: "the sound to phase",
		rate: "how fast the sweep goes, in cycles per second (Hz)",
		depth: "how far the sweep goes, in octaves either side of freq",
		freq: "the centre of the sweep, in Hz",
		oct: "moves the centre in octaves - patch modulation here",
		fb: "feedback: deepens the notches and makes the effect sharper",
		mix: "how much of the phased sound is added to the dry: 0 dry"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		var lfo = SinOsc.kr(\rate.kr(0.3 ! n, spec: [0.01, 10, \exp])) * \depth.kr(1.5 ! n, spec: [0, 4]);
		var freq = (\freq.kr(800 ! n, spec: \freq) * (2 ** (lfo + \oct.kr(0 ! n, spec: [-4, 4]))))
			.clip(30, 15000);
		// the first-order allpass coefficient for freq
		var t = (pi * freq / SampleRate.ir).tan;
		var a = (t - 1) / (t + 1);
		var mix = \mix.kr(0.5 ! n, spec: [0, 1]);
		var sig = in + (LocalIn.ar(n) * \fb.kr(0.3 ! n, spec: [0, 0.95]));
		4.do { sig = FOS.ar(sig, a, 1, a.neg) };
		LocalOut.ar(sig);
		(in + (sig * mix)) / (1 + mix)
	} }
)
