// chance - a coin for every gate: heads goes left, tails goes right
//
// With voices, a coin per voice, each control a value per voice: the heads
// of every voice first, then the tails.
(
	category: \sequencing,
	help: "a Bernoulli gate: each gate into trig (or each rate tick) goes to channel 1 with probability prob, otherwise to channel 2; with voices, a coin per voice",
	about: "Flips a coin for every gate: heads sends it to the first output, tails to the second. Patch a clock into trig and the two outputs into different drums or sequencers for variations that never quite repeat.",
	params: (
		trig: "each rising gate (above 0.5) flips the coin - patch a clock here",
		rate: "flips on its own clock, this many times per second; 0 off",
		prob: "the chance of heads: 1 always the first output, 0 always the second",
		glen: "how long each gate out stays high, in seconds"
	),
	outs: ["heads", "tails"],
	poly: { |n| {
		var gate = \trig.kr(0 ! n, spec: [0, 1]) > 0.5;
		var rate = \rate.kr(0 ! n, spec: [0, 50, 4]);
		var trig = ((Changed.kr(gate) * gate) + (Impulse.kr(rate) * (rate > 0))) > 0;
		var heads = TRand.kr(0, 1, trig) < \prob.kr(0.5 ! n, spec: [0, 1]);
		var glen = \glen.kr(0.05 ! n, spec: [0.001, 1, \exp]);
		[Trig.kr(trig * heads, glen), Trig.kr(trig * (1 - heads), glen)].flat
	} }
)
