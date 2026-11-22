// walk - a random walk: small steps, never far from where it was
//
// Each tick it moves a random distance up to step, and turns back at the
// edges. Patch a clock into clock to step in time; rate keeps ticking on
// its own as well, so set it to its lowest for the clock alone.
//
// With voices it is that many walkers, every control a value per voice,
// each drawing its own steps: they start together in the middle and drift
// apart.
(
	category: \modulation,
	help: "a random walk between lo and hi: step is the longest stride, rate how often (or clock, from outside), smooth glides between steps; with voices, a walker per voice",
	about: "A random walk: each tick it takes a small random step and turns back at the edges, so it drifts without jumping. A slow, wandering modulator for pitch, filters or position; with voices, walkers that drift apart.",
	params: (
		clock: "takes a step on each rising edge, on above 0.5 - patch a clock to walk in time",
		rate: "how often it steps on its own, per second; set it lowest to follow mostly the clock",
		step: "the longest stride per tick: small creeps, big leaps",
		smooth: "seconds it glides between steps; 0 steps",
		lo: "the bottom of the range it walks in",
		hi: "the top of the range it walks in"
	),
	poly: { |n| {
		var clock = \clock.kr(0 ! n, spec: [0, 1]) > 0.5;
		var trig = (Impulse.kr(\rate.kr(8 ! n, spec: [0.1, 100, \exp]))
			+ (Changed.kr(clock) * clock)) > 0;
		var prev = LocalIn.kr(n, 0.5);
		var next = (prev + (TRand.kr(-1, 1, trig) * \step.kr(0.05 ! n, spec: [0.001, 0.5, \exp])))
			.fold(0, 1);
		var pos = Select.kr(trig, [prev, next]);
		LocalOut.kr(pos);
		pos.lag(\smooth.kr(0.05 ! n, spec: [0, 2, 4]))
			.linlin(0, 1, \lo.kr(-1 ! n, spec: [-1, 1]), \hi.kr(1 ! n, spec: [-1, 1]), nil)
	} }
)
