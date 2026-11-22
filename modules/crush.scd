// crush - fewer samples, fewer bits
//
// With voices, a crusher for each, every control a value per voice.
(
	category: \effects,
	help: "a bitcrusher: rate is the sample rate it drops to, bits the resolution it keeps; mix; with voices, one per voice",
	about: "A bitcrusher: it drops the sample rate and the number of bits, for aliasing, grit and lo-fi digital crunch. Put it after anything that needs dirt.",
	params: (
		in: "the sound to crush",
		bits: "how many bits of resolution it keeps: lower is grainier",
		rate: "the sample rate it drops to, in Hz: lower gives more aliasing and a darker, fizzier sound",
		mix: "0 dry, 1 all crushed"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n);
		var steps = 2 ** (\bits.kr(8 ! n, spec: [1, 16]) - 1);
		var held = Latch.ar(in, Impulse.ar(\rate.kr(8000 ! n, spec: [100, 48000, \exp])));
		XFade2.ar(in, (held * steps).round / steps, \mix.kr(1 ! n, spec: [0, 1]) * 2 - 1)
	} }
)
