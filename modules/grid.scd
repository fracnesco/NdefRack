// grid - a drum pattern map: kick, snare and hat gates from a point on it
//
// After the Mutable Instruments Grids (Valley Topograph in the rack). Nine
// patterns of 32 steps sit on a three-by-three map, and x, y pick a point
// between them: every step has a weight, the downbeats heavy in every
// pattern, the rest of each pattern its own, blended from the four nearest.
// kick, snare and hat set how much of each weight is let through - at 0
// nothing, at 1 every step; chaos shakes the weights, a little at first and
// then into noise. Three channels of gates: kick, snare, hat; pick takes one
// out. On clock or rate; reset goes back to the first step.
//
// With voices it is that many players of the same map, each control a value
// per voice - a point, a density, a clock each: the kicks of every voice
// first, then the snares, then the hats.
(
	category: \sequencing,
	help: "a drum pattern map (Grids): x, y pick a place among nine 32-step patterns; kick, snare, hat are how dense each is; chaos shakes them; channels: kick, snare, hat gates; clock or rate, reset; with voices, a player per voice",
	about: "A drum pattern map: kick, snare and hat gates from a point on a map of nine patterns, so turning x and y glides between grooves. Patch a clock in and the three outputs into drums; chaos adds variations.",
	params: (
		clock: "each rising gate (above 0.5) moves one step - patch a clock here",
		rate: "steps on its own clock as well, this many per second; 0 off",
		reset: "fires to go back to the first step - patch a bar clock here",
		x: "one direction across the map of patterns",
		y: "the other direction across the map of patterns",
		chaos: "shakes the patterns: a little adds variations, a lot turns them into noise",
		kick: "how dense the kick is: 0 none, 1 every step",
		snare: "how dense the snare is: 0 none, 1 every step",
		hat: "how dense the hat is: 0 none, 1 every step",
		glen: "how long each gate stays high, in seconds"
	),
	outs: ["kick", "snare", "hat"],
	pages: [map: [\x, \y, \chaos, \kick, \snare, \hat], clock: [\clock, \rate, \reset, \glen]],
	poly: { |n| {
		var clock = \clock.kr(0 ! n, spec: [0, 1]) > 0.5;
		var trig = (Impulse.kr(\rate.kr(8 ! n, spec: [0, 50, 4]))
			+ (Changed.kr(clock) * clock)) > 0;
		var step = (PulseCount.kr(trig, \reset.tr(0 ! n)) - 1).max(0) % 32;
		// what every pattern has in common: the bar, the beats, the offbeats
		var metric = (0..31).collect { |i|
			case { i % 16 == 0 } { 1 } { i % 8 == 0 } { 0.8 } { i % 4 == 0 } { 0.6 } { i % 2 == 0 } { 0.4 } { 0.2 }
		};
		var base = [metric, metric.rotate(8), metric.collect { |m| 0.5 + (m * 0.5) }];
		// nine patterns, three instruments, 32 steps: the common ground and a hash of its own
		// (only read, so one table serves every voice)
		var table = LocalBuf.newFrom(9.collect { |node| 3.collect { |inst| 32.collect { |i|
			var hash = (sin((node * 12.9898) + (inst * 78.233) + (i * 37.719)) * 43758.5453).frac;
			(base[inst][i] * 0.6) + (hash * 0.4)
		} } }.flat);
		var gx = \x.kr(0.5 ! n, spec: [0, 1]) * 2, gy = \y.kr(0.5 ! n, spec: [0, 1]) * 2;
		var ix = gx.floor.min(1), iy = gy.floor.min(1);
		var fx = gx - ix, fy = gy - iy;
		var corners = [[0, 0, (1 - fx) * (1 - fy)], [1, 0, fx * (1 - fy)], [0, 1, (1 - fx) * fy], [1, 1, fx * fy]];
		var chaos = \chaos.kr(0 ! n, spec: [0, 1]);
		var density = [\kick.kr(0.5 ! n, spec: [0, 1]), \snare.kr(0.5 ! n, spec: [0, 1]), \hat.kr(0.5 ! n, spec: [0, 1])];
		var glen = \glen.kr(0.05 ! n, spec: [0.001, 1, \exp]);
		3.collect { |inst|
			var weight = corners.collect { |c|
				var node = (iy + c[1]) * 3 + (ix + c[0]);
				BufRd.kr(1, table, ((node * 3) + inst) * 32 + step, 0, 1) * c[2]
			}.sum + (TRand.kr(-1, 1, trig) * chaos * chaos);
			Trig.kr(trig * (weight > (1 - density[inst])), glen)
		}.flat
	} }
)
