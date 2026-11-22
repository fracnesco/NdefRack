// ambi - a sound placed among the speakers of a setup, on all of them
//
// Ambisonic panning: the direction is written as a few smooth patterns
// around the listener and every speaker plays its share of them, so the
// sound has the same size wherever it is - it does not narrow onto a
// speaker and widen between two, as vbap's does - and it moves evenly. It
// needs speakers spread evenly to place a sound exactly: on 5.1, where the
// back is nearly empty, it is the softer and vaguer of the two. vbap has
// the same controls, and can stand in for it.
//
// With voices, each voice is a sound of its own - its own in, direction,
// focus and level - and they all come out of the same speakers.
//
// The speakers are a setup from NdefRackSpeakers' table, picked in the
// drop-down on the module's face or in code - NdefRack.make(\ambi, speakers:
// \octo), NdefRack.setSpeakers(\ambi, \cube) - and the output is a channel
// per speaker, in the setup's order. azi goes once round from -1 to 1: 0 is
// straight ahead, 0.5 to the right, -0.5 to the left, 1 and -1 both behind,
// so a ramp from -1 to 1 circles without a jump. ele is the height, 1
// overhead; on speakers all at one height it only spreads the sound over
// them. focus is how tightly the speakers gather it: 0 the widest, every
// speaker in step; 0.5 the best for most rooms; 1 the sharpest, the far
// speakers pushing against the near ones. The level is the same in every
// direction, at every focus, and the same as vbap's.
//
// On a stereo pair it is a plain pan, hard right at azi 0.5. A setup with
// a sub (5.1, 7.1) has one more knob, sub: the input below 120 Hz, sent to
// the sub's channel. At 0 that channel is silent.
//
// It is all core UGens. The decoder is worked out by NdefRackSpeakers and
// given to the module as numbers (NdefRackSpeakers:data), so its code runs
// without the rack - the export writes them out.
(
	category: \spat,
	help: "a mono input among the speakers of a setup, a share on every one (ambisonics): azi once round from -1 to 1, 0 in front and 0.5 to the right; ele up; focus from wide to sharp; level - pick the speakers in the drop-down; with voices, each its own sound and place",
	about: "Places a mono sound among the speakers of a setup with ambisonics: every speaker plays a share, so the sound keeps its size as it moves and circles evenly. Pick the speakers in the drop-down; patch an lfo or a ramp into azi to move it.",
	params: (
		in: "the mono sound to place; with voices, each voice is a sound of its own",
		azi: "the direction around you: 0 in front, 0.5 right, -0.5 left, 1 and -1 behind - a ramp from -1 to 1 circles without a jump",
		ele: "the height: 1 overhead; on speakers all at one height it only spreads the sound over them",
		focus: "how tightly the speakers gather the sound: 0 widest, 0.5 best for most rooms, 1 sharpest",
		level: "the loudness of the placed sound, the same in every direction",
		sub: "only on setups with a sub: how much of the input below 120 Hz goes to the sub's channel; 0 leaves it silent"
	),
	speakers: { |s, n|
		{
			var in = \in.ar(0 ! n);
			var azi = \azi.kr(0 ! n, spec: [-1, 1]);
			var ele = \ele.kr(0 ! n, spec: [-1, 1]);
			var focus = \focus.kr(0.5 ! n, spec: [0, 1]);
			var level = \level.kr(1 ! n, spec: [0, 2]);
			var order = s[\order], w = s[\weights], dec = s[\decoder];
			// a direction (radians) as spherical harmonics, one array per order
			var harmonics = { |a, e|
				var ce = cos(e), x = cos(a) * ce, y = sin(a) * ce, z = sin(e);
				var x2 = x * x, y2 = y * y, z2 = z * z;
				switch (s[\mode],
					// along the line through the two speakers
					\pair, {
						[[1], [[x, y, z].collect { |c, i| if (s[\axis][i].abs > 1e-9) { c * s[\axis][i] } }
							.reject(_.isNil).sum * 2.sqrt]]
					},
					\ring, { [
						[1],
						[y, x] * 2.sqrt,
						[2 * x * y, x2 - y2] * 2.sqrt,
						[y * ((3 * x2) - y2), x * (x2 - (3 * y2))] * 2.sqrt
					].keep(order + 1) },
					{ [
						[1],
						[y, z, x] * 3.sqrt,
						[15.sqrt * x * y, 15.sqrt * y * z, (5.sqrt / 2) * ((3 * z2) - 1),
							15.sqrt * x * z, (15.sqrt / 2) * (x2 - y2)],
						[(35 / 8).sqrt * y * ((3 * x2) - y2), 105.sqrt * x * y * z,
							(21 / 8).sqrt * y * ((5 * z2) - 1), (7.sqrt / 2) * z * ((5 * z2) - 3),
							(21 / 8).sqrt * x * ((5 * z2) - 1), (105.sqrt / 2) * z * (x2 - y2),
							(35 / 8).sqrt * x * (x2 - (3 * y2))]
					].keep(order + 1) }
				)
			};
			// one gain per speaker: the harmonics weighted by focus - 0 in-phase,
			// 0.5 max-rE, 1 basic - through the decoder; the squares sum to 1
			var gains = { |a, e, fo|
				var f1 = (fo * 2).clip(0, 1), f2 = ((fo * 2) - 1).clip(0, 1);
				var v = harmonics.(a * pi, e * 0.5pi).collect { |group, k|
					if (k == 0) { group } {
						group * (w[0][k] + ((w[1][k] - w[0][k]) * f1) + ((w[2][k] - w[1][k]) * f2))
					}
				}.flat;
				var g;
				if (dec.isNil) { s[\numSpeakers].reciprocal.sqrt ! s[\numSpeakers] } {
					v = v / v.squared.sum.sqrt;
					g = dec.collect { |row| (row * v).sum };
					if (s[\exact]) { g } { g / g.squared.sum.sqrt.max(1e-9) }
				}
			};
			var out = [in, azi, ele, focus, level].collect(_.asArray).flop.collect { |v|
				v[0] * gains.(v[1], v[2], v[3]) * v[4]
			}.sum;
			// the sub's channel, where the setup has one: everything below 120 Hz
			var sub = if (s[\lfe].notNil) {
				LPF.ar(LPF.ar((in * level).asArray.sum, 120), 120) * \sub.kr(0, spec: [0, 1])
			};
			if (s[\lfe].isNil) { out } { out.asArray.copy.insert(s[\lfe], sub) }
		}
	}
)
