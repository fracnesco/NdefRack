// plate - a plate reverb: Dattorro's tank, with a size
//
// After the Valley Plateau, which is after Dattorro's 1997 plate: four
// allpasses diffuse the input, then two halves of a tank - a modulated
// allpass, a delay, damping, another allpass, a delay - feed each other,
// and the output is tapped from seven places in each. size scales every
// delay, a small plate to a hall; decay is how much comes round each time
// (1 goes on for ever); damp darkens the tail, bright the input; mod is the
// wobble that keeps the tail from ringing; predelay, mix. Stereo in and
// out; a mono cable feeds both sides. With more than two voices at in, they
// are spread across the pair first (Splay), voice 1 on the left; the other
// controls are for the whole plate.
(
	category: \effects,
	help: "a Dattorro plate (Plateau): size scales the tank, decay (1 is for ever), damp darkens the tail, bright the input, mod wobbles it; predelay, mix; stereo in and out, a mono cable feeds both sides; more than two voices are spread across the pair",
	about: "A plate reverb, smooth and dense, from a small plate to a large hall with size. decay sets how long the tail lasts, damp darkens it. Stereo in and out; put it at the end of a chain.",
	params: (
		in: "the sound to reverberate; a mono cable feeds both sides",
		size: "the size of the space: smaller is tight and metallic, larger a hall",
		decay: "how long the tail lasts: 1 goes on for ever",
		damp: "darkens the tail as it dies away",
		bright: "how bright the sound going into the reverb is: lower is darker",
		mod: "a slow wobble in the tail that stops it ringing on one pitch",
		predelay: "a gap before the reverb starts, in seconds",
		mix: "0 dry, 1 all wet"
	),
	poly: { |n| {
		var in = \in.ar(0 ! n.max(2));
		var dry = if (n > 2) { Splay.ar(in) } {
			[in[0], Select.ar(Amplitude.kr(in[1], 0.01, 2) > 0.0001, [in[0], in[1]])]
		};
		var fs = 29761;   // Dattorro's sample rate: his delays are in its samples
		var size = \size.kr(1, spec: [0.5, 2, \exp]);
		var t = { |samples| samples / fs * size };
		// an allpass with coefficient g (negative g, negative feedback)
		var ap = { |sig, samples, g, mod = 0|
			var d = t.(samples);
			AllpassC.ar(sig, 0.25, d + mod, d * log(0.001) / log(g.abs) * g.sign)
		};
		var tap = { |sig, samples| DelayC.ar(sig, 0.35, t.(samples)) };
		var decay = \decay.kr(0.5, spec: [0, 1]);
		var damp = \damp.kr(0.3, spec: [0, 0.95]);
		var mod = SinOsc.kr([0.7, 1.1], [0, 0.5pi]) * \mod.kr(0.3, spec: [0, 1]) * t.(12);
		var sum = (dry[0] + dry[1]) * 0.5;
		var diff = OnePole.ar(DelayN.ar(sum, 0.25, \predelay.kr(0.02, spec: [0, 0.25])),
			1 - \bright.kr(0.7, spec: [0.05, 1]));
		var tank = LocalIn.ar(2);
		var l1, l2, r1, r2, left, right2;
		diff = ap.(diff, 142, 0.75);
		diff = ap.(diff, 107, 0.75);
		diff = ap.(diff, 379, 0.625);
		diff = ap.(diff, 277, 0.625);
		l1 = ap.(diff + tank[1], 672, -0.7, mod[0]);
		l2 = ap.(OnePole.ar(tap.(l1, 4453), damp) * decay, 1800, 0.5);
		r1 = ap.(diff + tank[0], 908, -0.7, mod[1]);
		r2 = ap.(OnePole.ar(tap.(r1, 4217), damp) * decay, 2656, 0.5);
		LocalOut.ar([tap.(l2, 3720), tap.(r2, 3163)]);
		left = tap.(r1, 266) + tap.(r1, 2974) - tap.(r2, 1913) + tap.(r2, 1996)
			- tap.(l1, 1990) - tap.(l2, 187) - tap.(l2, 1066);
		right2 = tap.(l1, 353) + tap.(l1, 3627) - tap.(l2, 1228) + tap.(l2, 2673)
			- tap.(r1, 2111) - tap.(r2, 335) - tap.(r2, 121);
		XFade2.ar(dry, [left, right2] * 0.6, \mix.kr(0.35, spec: [0, 1]) * 2 - 1)
	} }
)
