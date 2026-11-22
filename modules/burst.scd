// burst - one gate in, a burst of gates out
//
// After the Befaco Burst. Each gate into trig (or each rate tick) fires
// count gates, time apart; accel makes the burst speed up (below 0, the
// last gate comes 2^accel times as soon as the first) or slow down; prob
// lets each gate through by chance - a ratchet, a roll, a stutter. Channel
// 1 is the gates; channel 2 a trigger with the last gate of the burst.
//
// With voices it is that many bursts, each control a value per voice: the
// gates of every voice first, then the last-gate triggers of every voice.
(
	category: \sequencing,
	help: "a burst of count gates, time apart, on each trig (or rate tick): accel speeds it up or slows it down, prob lets each gate through by chance; channels: gates, a trigger with the last one; with voices, a burst per voice",
	about: "Turns one gate into a quick burst of gates - a ratchet, a roll, a stutter. The burst can speed up or slow down and each gate can be let through by chance. Patch a clock or sequencer gate into trig and the output into a drum.",
	params: (
		trig: "each rising gate (above 0.5) starts a burst - patch a clock or sequencer gate here",
		rate: "starts bursts on its own clock, this many per second; 0 off",
		count: "how many gates in each burst",
		time: "the time between gates in the burst, in seconds",
		accel: "below 0 the burst speeds up, above 0 it slows down; 0 keeps it even",
		prob: "the chance each gate gets through: 1 all of them, 0 none",
		glen: "how long each gate stays high, in seconds"
	),
	outs: ["gates", "last"],
	poly: { |n| {
		var gate = \trig.kr(0 ! n) > 0.5;
		var rate = \rate.kr(0 ! n, spec: [0, 20, 4]);
		var fire = (Changed.kr(gate) * gate) + (Impulse.kr(rate) * (rate > 0));
		var count = \count.kr(4 ! n, spec: [1, 16, \lin, 1]).round.max(1);
		var time = \time.kr(0.1 ! n, spec: [0.01, 2, \exp]);
		var accel = \accel.kr(0 ! n, spec: [-2, 2]);
		var state = LocalIn.kr(2 * n);
		// gates still to fire, and seconds until the next - n of each
		var left = state[0 .. n - 1].unbubble, timer = state[n ..].unbubble;
		var dt = ControlDur.ir;
		var tick, k, done;
		left = Select.kr(fire > 0, [left, count]);
		timer = Select.kr(fire > 0, [timer - dt, 0]);
		tick = (left > 0) * (timer <= 0);
		k = count - left;   // 0 at the first gate
		timer = Select.kr(tick, [timer, time * (2 ** (accel * k / (count - 1).max(1)))]);
		left = left - tick;
		done = tick * (left <= 0);
		LocalOut.kr([left, timer].flat);
		[
			Trig.kr(tick * (TRand.kr(0, 1, tick) < \prob.kr(1 ! n, spec: [0, 1])),
				\glen.kr(0.02 ! n, spec: [0.001, 1, \exp])),
			Trig.kr(done, 0.01)
		].flat
	} }
)
