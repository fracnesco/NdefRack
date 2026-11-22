// grains - the sound just gone, cut into grains and strewn
//
// After the Mutable Instruments Clouds. The last two seconds of in are kept
// in a loop, and grains are read out of it: position is how far back they
// start (0 now, 1 two seconds ago), jitter scatters them around it, size is
// how long each lasts, density how many a second - regular, or scattered at
// random the more random is turned up. oct plays them higher or lower, width
// spreads them across the stereo pair. freeze stops the loop recording, and
// what it holds is played over and over; fb puts the grains back into the
// loop. mix from dry to grains.
//
// With voices at in, each voice has its own loop and its own grains, their
// pan centred on the voice's place across the pair (voice 1 on the left, as
// Splay), and the clouds are added at 1 / sqrt(n); the dry voices are
// Splayed. The other controls are for the whole module: the output stays a
// stereo pair. Each voice's loop is two seconds of server memory.
(
	category: \effects,
	help: "a granular texture (Clouds) from the last two seconds of in: position back in time, jitter, size, density (random scatters them), oct, width; freeze keeps the loop as it is; fb, mix; with voices at in, a loop and a cloud per voice across the pair",
	about: "A granular cloud from the last two seconds of the input: small grains read back from the past, scattered in time, pitch and stereo, for textures and smears. freeze holds what it has and loops it; the output is a stereo pair.",
	params: (
		in: "the sound to granulate; with voices, each voice gets its own loop and cloud",
		freeze: "on stops recording and keeps playing grains from what the loop holds",
		density: "how many grains a second",
		random: "how scattered the grains are in time: 0 a steady stream, 1 fully random",
		size: "how long each grain lasts, in seconds",
		position: "how far back grains start: 0 just now, 1 two seconds ago",
		jitter: "scatters each grain's start around position",
		width: "how widely grains are spread across the stereo pair",
		oct: "plays the grains higher or lower, in octaves",
		fb: "puts the grains back into the loop, building up a denser, smeared texture",
		mix: "0 dry, 1 only grains"
	),
	pages: [grains: [\position, \jitter, \size, \density, \random], sound: [\freeze, \oct, \width, \fb, \mix]],
	poly: { |n| {
		var ins = \in.ar(0 ! n).asArray;
		var freeze = \freeze.kr(0, spec: [0, 1, \lin, 1]) > 0.5;
		var density = \density.kr(20, spec: [1, 200, \exp]);
		var random = \random.kr(0.3, spec: [0, 1]);
		var size = \size.kr(0.1, spec: [0.01, 1, \exp]);
		var position = \position.kr(0.2, spec: [0, 1]);
		var jitter = \jitter.kr(0.1, spec: [0, 1]);
		var width = \width.kr(0.5, spec: [0, 1]);
		var rate = 2 ** \oct.kr(0, spec: [-2, 2]);
		var amount = \fb.kr(0, spec: [0, 1]);
		var fbs = LocalIn.ar(n).asArray;
		// a voice at a time: a LocalBuf is made once, so each voice makes its own
		var wets = ins.collect { |in, i|
			var buf = LocalBuf(SampleRate.ir * 2).clear;
			var frames = BufFrames.kr(buf);
			var head = Phasor.ar(0, 1 - freeze, 0, frames);
			var trig = (Impulse.ar(density * (1 - random)) + Dust.ar(density * random)) > 0;
			var back = (position + (TRand.ar(0, 1, trig) * jitter)).clip(0, 1) * 0.9 + 0.05;
			var pos = ((head / frames) - back).wrap(0, 1);
			var pan = TRand.ar(-1, 1, trig) * width;
			var wet, write;
			if (n > 1) { pan = (pan + (i / (n - 1) * 2 - 1)).clip(-1, 1) };
			wet = GrainBuf.ar(2, trig, size, buf, rate, pos, 4, pan);
			write = in + (fbs[i] * amount).tanh;
			BufWr.ar(Select.ar(freeze, [write, BufRd.ar(1, buf, head, 1, 1)]), buf, head);
			wet
		};
		LocalOut.ar(wets.collect { |w| w.sum * 0.5 });
		if (n > 1) {
			XFade2.ar(Splay.ar(ins), wets.sum * n.sqrt.reciprocal, \mix.kr(0.5, spec: [0, 1]) * 2 - 1)
		} {
			XFade2.ar([ins[0], ins[0]], wets[0], \mix.kr(0.5, spec: [0, 1]) * 2 - 1)
		}
	} }
)
