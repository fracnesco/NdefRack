// vbap - a sound placed among the speakers of a setup, between the nearest
//
// requires: VBAP
//
// Vector base amplitude panning (sc3-plugins' VBAP): the sound comes from
// the two speakers either side of where it is aimed - the three around it,
// when the speakers have height - and from no other, as loud on one speaker
// as between two. It is the sharpest way to place a sound, and the one to
// take when the speakers are not evenly spread. ambi has the same controls,
// and can stand in for it.
//
// With voices, each voice is a sound of its own - its own in, direction,
// spread and level - and they all come out of the same speakers.
//
// The speakers are a setup from NdefRackSpeakers' table, picked in the
// drop-down on the module's face or in code - NdefRack.make(\vbap, speakers:
// \octo), NdefRack.setSpeakers(\vbap, '5.1') - and the output is a channel
// per speaker, in the setup's order. azi goes once round from -1 to 1: 0 is
// straight ahead, 0.5 to the right, -0.5 to the left, 1 and -1 both behind,
// so a ramp from -1 to 1 circles without a jump. ele is the height, 1
// overhead; speakers all at one height do not read it. spread opens the
// sound onto the speakers around, 1 all of them. Where the speakers leave
// off - past the ends of a stereo pair, below the floor of a dome - the
// sound stays on the last of them.
//
// A setup with a sub (5.1, 7.1) has one more knob, sub: the input below
// 120 Hz, sent to the sub's channel. At 0 that channel is silent.
// The module is given the setup as numbers (NdefRackSpeakers:data), so
// its code runs without the rack - the export writes them out.
(
	category: \spat,
	help: "a mono input among the speakers of a setup, between the nearest two or three (VBAP): azi once round from -1 to 1, 0 in front and 0.5 to the right; ele up; spread onto the neighbours; level - pick the speakers in the drop-down; with voices, each its own sound and place",
	about: "Places a sound among the speakers of a setup, between the nearest two or three (VBAP) - the sharpest placement, and good for unevenly spread speakers. Patch an lfo or ramp into azi to move it around.",
	params: (
		in: "the mono sound to place",
		azi: "the direction: 0 in front, 0.5 right, -0.5 left, 1 and -1 behind - a -1..1 ramp circles",
		ele: "the height: 1 overhead; speakers all at one height ignore it",
		spread: "opens the sound onto the neighbouring speakers: 0 sharp, 1 all of them",
		level: "the output level",
		sub: "only with a sub (5.1, 7.1): how much of the input below 120 Hz goes to the sub; 0 silent"
	),
	speakers: { |s, n|
		var data = s[\vbapData];
		if (data.isNil) {
			("vbap: VBAP cannot pan among the speakers of % - the module is silent on them"
				.format(s[\name].asCompileString)).warn;
		};
		{
			var in = \in.ar(0 ! n);
			var azi = \azi.kr(0 ! n, spec: [-1, 1]);
			var ele = \ele.kr(0 ! n, spec: [-1, 1]);
			var spread = \spread.kr(0 ! n, spec: [0, 1]);
			var level = \level.kr(1 ! n, spec: [0, 2]);
			var ar = s[\aziRange], er = s[\eleRange];
			var buf = data !? { LocalBuf.newFrom(data) };
			// one voice's direction in degrees, kept where VBAP can take it:
			// inside an arc of speakers, between the lowest and highest
			var angles = { |a, e|
				a = a * 180;
				e = e * 90;
				if (ar.notNil) {
					var mid = (ar[0] + ar[1]) / 2, half = (ar[1] - ar[0]) / 2;
					a = (a - mid).wrap(-180.0, 180.0).clip(half.neg, half) + mid;
				};
				if (er.notNil and: { (er[0] > -90) or: { er[1] < 90 } }) { e = e.clip(er[0], er[1]) };
				[a, e]
			};
			var out = if (data.isNil) { DC.ar(0) ! s[\numSpeakers] } {
				[in, azi, ele, spread, level].collect(_.asArray).flop.collect { |v|
					var ae = angles.(v[1], v[2]);
					VBAP.ar(s[\numSpeakers], v[0], buf, ae[0], ae[1], v[3] * 100).asArray * v[4]
				}.sum
			};
			// the sub's channel, where the setup has one: everything below 120 Hz
			var sub = if (s[\lfe].notNil) {
				LPF.ar(LPF.ar((in * level).asArray.sum, 120), 120) * \sub.kr(0, spec: [0, 1])
			};
			if (s[\lfe].isNil) { out } { out.asArray.copy.insert(s[\lfe], sub) }
		}
	}
)
