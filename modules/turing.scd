// turing - a Turing Machine: a loop of random bits that slowly forgets itself
//
// After the Music Thing Modular Turing Machine. A circular memory of
// length bits goes round once every length clocks, and as each bit comes
// back round, change is the chance it is flipped. At 0 the loop is locked
// and repeats; at 1 every bit flips, and the loop is locked at twice the
// length; in between it drifts, more the further from either end.
//
// The newest eight bits, newest heaviest, are the value on channel 1, so
// each step is half the last one plus the new bit - the melodic drift the
// hardware is known for. A newest bit of 1 is a gate on channel 2.
//
// With voices it is that many machines, each with a memory of its own,
// seeded apart, and every control a value per voice: the values of every
// voice first, then the gates.
(
	category: \sequencing,
	help: "a looping random sequence: change 0 locks the loop, 0.5 is random, 1 locks it at double length; length 2..16; channels: value, gate; with voices, a machine per voice, each its own memory",
	about: "After the Music Thing Turing Machine: a loop of random bits that repeats, slowly changes or wanders freely, depending on change. Outputs a drifting stepped value and a gate - patch them into pitch and an envelope.",
	params: (
		clock: "steps on each rising edge, on above 0.5 - patch a clock or gate here",
		rate: "its own clock, in steps per second; 0 to follow only the clock input",
		length: "how many steps the loop has",
		change: "the chance a bit flips as it comes round: 0 locked, 0.5 random, 1 locked at twice the length",
		lo: "the lowest value out",
		hi: "the highest value out",
		glen: "how long each gate stays on, in seconds"
	),
	outs: ["value", "gate"],
	poly: { |n| {
		var clocks = \clock.kr(0 ! n, spec: [0, 1]).asArray;
		var rates = \rate.kr(4 ! n, spec: [0, 50, 4]).asArray;
		var lengths = \length.kr(16 ! n, spec: [2, 16, \lin, 1]).asArray;
		var changes = \change.kr(0.1 ! n, spec: [0, 1]).asArray;
		var los = \lo.kr(0 ! n, spec: [-1, 1]).asArray;
		var his = \hi.kr(1 ! n, spec: [-1, 1]).asArray;
		var glens = \glen.kr(0.05 ! n, spec: [0.001, 1, \exp]).asArray;
		// a machine per voice: a memory written and read is state, so each
		// voice is built on its own
		n.collect { |v|
			var clock = clocks[v] > 0.5;
			var trig = (Impulse.kr(rates[v]) + (Changed.kr(clock) * clock)) > 0;
			var len = lengths[v].round.clip(2, 16);
			// the circular memory, sixteen bits, seeded at random when built
			var mem = LocalBuf.newFrom({ 2.rand } ! 16);
			var pos = PulseCount.kr(trig) % len;
			// the bit written length steps ago, on its way round again
			var old = BufRd.kr(1, mem, pos, 0, 1);
			var flip = TRand.kr(0, 1, trig) < changes[v];
			// only a clock changes the memory; in between, it is read as it is
			var bit = Select.kr(trig, [old, (old - flip).abs]);
			var bits = [bit] ++ (1..7).collect { |i| BufRd.kr(1, mem, (pos - i) % len, 0, 1) };
			BufWr.kr(bit, mem, pos, 0);
			[
				((bits * [128, 64, 32, 16, 8, 4, 2, 1]).sum / 255)
					.linlin(0, 1, los[v], his[v], nil),
				Trig.kr(trig * bit, glens[v])
			]
		}.flop.flat
	} }
)
