// sh - sample and hold: what the input was at the last clock
//
// With noise at 1 - where it starts - it samples its own white noise, for
// stepped random values; turn noise down to sample what is patched in.
//
// With voices, a sample and hold for each, every control a value per voice
// and a noise of its own, so one clock into all of them gives a different
// random value on each.
(
	category: \modulation,
	help: "sample and hold: in is sampled at each clock (or rate tick); noise 1 samples white noise instead, for stepped random; with voices, one per voice, each its own noise",
	about: "Sample and hold: on each clock it grabs the input and holds it until the next. With noise at 1 it samples its own white noise for stepped random values. Patch a clock in and the output into pitch or a filter.",
	params: (
		clock: "takes a new sample on each rising edge, on above 0.5 - patch a clock or gate here",
		rate: "its own clock, in samples per second; 0 to follow only the clock input",
		noise: "what is sampled: 0 the input, 1 its own white noise for random steps; in between a blend",
		in: "the signal to sample - an lfo, an envelope, any modulation"
	),
	poly: { |n| {
		var clock = \clock.kr(0 ! n, spec: [0, 1]) > 0.5;
		var trig = (Impulse.kr(\rate.kr(4 ! n, spec: [0, 50, 4]))
			+ (Changed.kr(clock) * clock)) > 0;
		var src = SelectX.kr(\noise.kr(1 ! n, spec: [0, 1]),
			[\in.kr(0 ! n, spec: [-1, 1]), Array.fill(n, { WhiteNoise.kr }).unbubble]);
		Latch.kr(src, trig)
	} }
)
