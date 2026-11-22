// NdefRack - Copyright (c) 2026 Francesco Corvi
// MIT License; see the LICENSE file in this repository.

// =====================================================================
// NdefRackSpeakers - where the speakers are
//
// A table of named speaker setups, and what the panning modules need to
// know about each: how many channels it has, where every speaker stands,
// and the numbers that put a sound somewhere between them. No window and
// no server - everything here is worked out in the language.
//
//   NdefRackSpeakers.names;              // [\stereo, \quad, ... \dome24]
//   NdefRackSpeakers(\octo);             // one setup
//   NdefRackSpeakers(\octo).dirs;        // [[-22.5, 0], [22.5, 0], ...]
//   NdefRackSpeakers('5.1').numChannels; // 6 - five speakers and the sub
//
//   NdefRackSpeakers.add(\studio, [[-30, 0], [30, 0], [-110, 0], [110, 0]]);
//   NdefRackSpeakers.add('my room', [-45, 45, 0, 135, -135], 5, "room 5.1");
//   NdefRackSpeakers.remove(\studio);
//
// A direction is [azimuth, elevation] in degrees, seen from the middle of
// the room: azimuth 0 is straight ahead and POSITIVE IS TO THE RIGHT (30
// is the right speaker of a stereo pair, -110 the left surround), ±180
// behind; elevation 0 is ear height, positive is up, 90 overhead. A plain
// number is an azimuth at ear height. The directions are in the order of
// the output channels. The sub has no direction and is not among them:
// lfe is the index of its output channel (3 in 5.1 - L R C LFE Ls Rs), and
// the modules send it nothing but what their sub control says.
//
// The setups that come with the rack cannot be replaced or removed; add
// your own under another name (2 to 55 speakers). Adding a name again with
// other speakers makes a NEW setup object - a setup never changes once
// made, so whatever was built for the old one stays right for it - and
// tells the dependants: NdefRackSpeakers.changed(\setups, name). Adding
// exactly what is there already does nothing.
//
// Two ways to pan come out of a setup.
//
// ambiGains(azi, ele, focus) - a gain per speaker, from numbers or from
// control rate signals, with core UGens only. The direction is written in
// harmonics (circular on a ring, spherical when speakers have height, real,
// N2D/N3D), weighted by focus - 0 in-phase, every speaker in step and the
// widest image; 0.5 max-rE; 1 basic, the sharpest, with the speakers
// opposite out of phase - and decoded by the matrix with orthonormal
// columns nearest to the harmonics sampled at the speakers, so the sum of
// the squared gains is 1 in every direction, at every focus, on every
// setup. The order is chosen once per setup: the highest, 3 at most, with
// no more harmonics than speakers, whose energy vector at max-rE points
// within 45 degrees of where it is told, every 15 degrees of azimuth
// between the lowest and the highest speaker; 1 otherwise. So a square has
// order 1, a hexagon 2, an octagon 3, 5.0 only 1 (at 2 its rear points the
// wrong way).
//   mode says which harmonics: \pair - two speakers, the sound moves along
//   the line between them and the rear folds onto the front (hard right is
//   azimuth 90, not 30); \ring - every speaker at one height, elevation
//   only widens the image; \sphere - anything with height.
//
// vbapData - what sc3-plugins' VBAP wants in its buffer
// (VBAPSpeakerArray:getSetsAndMatrices), nil when that class is missing or
// the setup gives it nothing to work with: two speakers opposite each
// other, speakers with height that all lie in one plane through the
// listener. Measured on the server, VBAP puts 1.0 on a speaker aimed at
// and keeps the power at 1 - inside the speakers. Outside them it keeps the
// power and goes wrong, so what goes into it has to be clipped, and
// vbapAngles(azi, ele) does that:
//   - a ring with a gap wider than 170 degrees is an arc (a stereo pair, a
//     front wall). Past its ends the far speaker comes out with its phase
//     turned over (stereo, azimuth 90: [-0.71, 0.71]). aziRange is the arc,
//     [from, to] going right, `to` above 180 when it runs through the
//     back; nil on a closed ring. VBAPSpeakerArray also joins the two ends
//     of an arc of three speakers or more into a pair of their own, which
//     then takes the whole front (L C R aimed at C: C silent); vbapData
//     leaves that pair out.
//   - speakers with height, and nothing under the listener (a dome, 5.1.4):
//     below the lowest speakers the sound lands on whatever triangle is
//     least wrong, at full power (dome13 aimed 10 degrees down drifts
//     sideways; straight down comes from overhead). eleRange is [lowest,
//     highest] elevation VBAP can be asked for: the lowest speaker when no
//     triangle of speakers holds straight down, -90 when one does (a cube);
//     the same upward. nil on a ring or a pair, where VBAP does not read
//     the elevation at all.
//
// withLfe(channels, sub) puts the sub's channel in its place, and addCode
// is the line that makes the setup again, for a patch written out as code.
// =====================================================================

NdefRackSpeakers {
	classvar table, sequence;
	// the harmonics a setup may have at most, and how far from where it is
	// told a setup's energy vector may point before a lower order is taken
	classvar <>maxOrder = 3, <>maxError = 45;

	var <name, <dirs, <lfe, <label, <builtin;
	var mode, order, decoder, exact, axis, vbapData, vbapDone = false;
	var aziRange, eleRange, rangesDone = false;

	*initClass {
		table = IdentityDictionary.new;
		sequence = Array.new;
		this.prBuiltins.do { |b| this.prPut(this.prNew(b[0], b[1], b[2], b[3], true)) };
	}

	*new { |name| ^this.at(name) }

	*at { |name| ^name !? { table.at(name.asSymbol) } }

	// a name, a setup or nothing -> the setup; nil for a name not in the table
	*for { |setup|
		^case
			{ setup.isNil } { this.at(this.default) }
			{ setup.isKindOf(this) } { setup }
			{ this.at(setup) }
	}

	*default { ^\stereo }

	// the ones that come with the rack first, then the user's, in the order made
	*names { ^sequence.select { |n| table[n].builtin } ++ this.userNames }

	*userNames { ^sequence.reject { |n| table[n].builtin } }

	*add { |name, dirs, lfe, label|
		var old, new, problem;
		if (name.isNil or: { name.asString.isEmpty }) {
			"NdefRackSpeakers: a setup needs a name".warn;
			^nil
		};
		name = name.asSymbol;
		old = table.at(name);
		if (old.notNil and: { old.builtin }) {
			("NdefRackSpeakers: % comes with the rack and stays as it is - add yours under another name"
				.format(name.asCompileString)).warn;
			^nil
		};
		problem = this.prProblem(dirs, lfe);
		if (problem.notNil) {
			("NdefRackSpeakers: % not added - %".format(name.asCompileString, problem)).warn;
			^nil
		};
		dirs = this.prDirs(dirs);
		label = (label ? name).asString;
		if (old.notNil and: { old.sameAs(dirs, lfe) } and: { old.label == label }) { ^old };
		new = this.prNew(name, dirs, lfe, label, false);
		this.prPut(new);
		this.changed(\setups, name);
		^new
	}

	*remove { |name|
		var old;
		name = name !? { name.asSymbol };
		old = table.at(name);
		if (old.isNil) { ^nil };
		if (old.builtin) {
			("NdefRackSpeakers: % comes with the rack and cannot be removed"
				.format(name.asCompileString)).warn;
			^nil
		};
		table.removeAt(name);
		sequence = sequence.reject { |n| n == name };
		this.changed(\setups, name);
		^old
	}

	// ---- one setup ---------------------------------------------------

	numSpeakers { ^dirs.size }

	numChannels { ^dirs.size + lfe.notNil.binaryValue }

	// is this what the table holds under its name? false once it was
	// replaced or removed
	isCurrent { ^table.at(name) === this }

	// the same speakers in the same order, and the same sub
	sameAs { |otherDirs, otherLfe|
		var other = this.class.prProblem(otherDirs, otherLfe);
		if (other.notNil) { ^false };
		otherDirs = this.class.prDirs(otherDirs);
		^(otherLfe == lfe) and: { otherDirs.size == dirs.size } and: {
			dirs.every { |d, i|
				((d[0] - otherDirs[i][0]).wrap(-180.0, 180.0).abs < 1e-6)
					and: { (d[1] - otherDirs[i][1]).abs < 1e-6 }
			}
		}
	}

	// \pair, \ring or \sphere - see the top of the file
	mode {
		^mode ?? {
			var eles = dirs.collect(_[1]);
			mode = case
				{ dirs.size < 3 } { \pair }
				{ (eles.maxItem - eles.minItem) < 0.5 } { \ring }
				{ \sphere }
		}
	}

	// the order ambiGains works at
	order { ^order ?? { order = this.prOrderFor } }

	// speakers x harmonics, at this setup's order
	decoder {
		^decoder ?? {
			var made = this.prDecoder(this.order);
			exact = made[1];
			decoder = made[0]
		}
	}

	// one gain per speaker for a direction in degrees - numbers in, numbers
	// out; control rate signals in, control rate signals out. focus 0
	// in-phase, 0.5 max-rE, 1 basic. The squares sum to 1.
	ambiGains { |azi = 0, ele = 0, focus = 0.5|
		var dec = this.decoder;
		^this.prGains(dec, exact, this.order, azi, ele, focus)
	}

	// how well a direction comes out: (power:, amp:, re:, minG:, err:) - the
	// sum of the squares, the sum, the length of the energy vector, the
	// lowest gain, and how many degrees the energy vector is off. Numbers
	// only. An order of your own builds that decoder for the question.
	measure { |azi = 0, ele = 0, focus = 0.5, atOrder|
		var gains = if (atOrder.isNil) { this.ambiGains(azi, ele, focus) } {
			var made = this.prDecoder(atOrder);
			this.prGains(made[0], made[1], atOrder, azi, ele, focus)
		};
		^this.measureGains(gains, azi, ele)
	}

	measureGains { |gains, azi = 0, ele = 0|
		var power = gains.squared.sum, aim = this.class.prUnit(azi, ele);
		var re = dirs.collect { |d, i| this.class.prUnit(d[0], d[1]) * gains[i].squared }.sum
			/ power.max(1e-30);
		var mag = re.squared.sum.sqrt;
		^(
			power: power, amp: gains.sum, re: mag, minG: gains.minItem,
			err: if (mag < 1e-9) { 90.0 } { ((re * aim).sum / mag).clip(-1, 1).acos * 180 / pi }
		)
	}

	// the data for VBAP's buffer, or nil - see the top of the file
	vbapData {
		if (vbapDone.not) {
			vbapDone = true;
			vbapData = this.prVbapData;
		};
		^vbapData
	}

	// [from, to] in degrees when the speakers are an arc VBAP must stay
	// inside, nil when they go all the way round
	aziRange {
		this.prRanges;
		^aziRange
	}

	// [lowest, highest] elevation VBAP can be asked for, nil when it does
	// not read the elevation
	eleRange {
		this.prRanges;
		^eleRange
	}

	// a direction as VBAP can take it on this setup: [azi, ele] in degrees,
	// from numbers or control rate signals, kept inside aziRange and eleRange
	vbapAngles { |azi = 0, ele = 0|
		var ar = this.aziRange, er = this.eleRange;
		if (ar.notNil) {
			var mid = (ar[0] + ar[1]) / 2, half = (ar[1] - ar[0]) / 2;
			azi = (azi - mid).wrap(-180.0, 180.0).clip(half.neg, half) + mid;
		};
		if (er.notNil and: { (er[0] > -90) or: { er[1] < 90 } }) {
			ele = ele.clip(er[0], er[1]);
		};
		^[azi, ele]
	}

	// what VBAP makes of a direction, worked out here from vbapData the way
	// the UGen does at no spread: nil without data. Numbers only - for
	// checks, and for eleRange.
	vbapGains { |azi = 0, ele = 0|
		var data = this.vbapData, u, best, bestMin = -inf, gains;
		if (data.isNil) { ^nil };
		u = this.class.prUnit(azi, ele);
		if (data[0] == 3) {
			data.drop(2).clump(21).do { |set|
				var g = 3.collect { |j| (u * set[(3 + (j * 3)) .. (5 + (j * 3))]).sum };
				if (g.minItem > bestMin) { bestMin = g.minItem; best = [set.keep(3), g] };
			}
		} {
			data.drop(2).clump(6).do { |set|
				var g = [(u[0] * set[2]) + (u[1] * set[3]), (u[0] * set[4]) + (u[1] * set[5])];
				if (g.minItem > bestMin) { bestMin = g.minItem; best = [set.keep(2), g] };
			}
		};
		gains = 0.0 ! dirs.size;
		best[1] = best[1] / best[1].squared.sum.sqrt.max(1e-30);
		best[0].do { |index, i| gains[index.asInteger - 1] = best[1][i] };
		^gains
	}

	// is there a pair or a triangle of speakers around this direction?
	vbapCovers { |azi = 0, ele = 0|
		var gains = this.vbapGains(azi, ele);
		^gains.notNil and: { gains.minItem > -1e-4 }
	}

	// the speakers' channels with the sub's put in at its place; as they
	// are when the setup has no sub
	withLfe { |channels, sub = 0.0|
		^if (lfe.isNil) { channels } { channels.asArray.copy.insert(lfe, sub) }
	}

	// Everything vbap and ambi need, as plain numbers in an Event - what
	// their speakers: makers are given, so the functions they make, and the
	// code the rack exports for them, need no NdefRackSpeakers: (name:,
	// dirs:, numSpeakers:, lfe:, mode:, order:, decoder:, exact:, weights:, axis:,
	// vbapData:, aziRange:, eleRange:). See ambiGains and vbapAngles for what
	// the numbers are for.
	data {
		var n = this.order;
		^(
			name: name,
			dirs: dirs,
			numSpeakers: dirs.size,
			lfe: lfe,
			mode: this.mode,
			order: n,
			decoder: this.decoder,
			exact: exact,
			weights: this.prWeights(n),
			axis: if (this.mode == \pair) { this.prAxis },
			vbapData: this.vbapData,
			aziRange: this.aziRange,
			eleRange: this.eleRange
		)
	}

	// the line that adds this setup
	addCode {
		var args = [
			this.class.prSym(name),
			"[" ++ dirs.collect { |d| "[%, %]".format(d[0], d[1]) }.join(", ") ++ "]"
		];
		var plain = label == name.asString;
		if (lfe.notNil or: { plain.not }) { args = args.add(lfe.asCompileString) };
		if (plain.not) { args = args.add(label.asCompileString) };
		^"NdefRackSpeakers.add(%);".format(args.join(", "))
	}

	storeOn { |stream| stream << "NdefRackSpeakers(" << name.asCompileString << ")" }

	printOn { |stream| this.storeOn(stream) }

	// ---- ambisonics --------------------------------------------------

	// the direction in harmonics, one array per order; azi and ele in
	// degrees, numbers or signals
	prHarmonics { |azi, ele, n|
		var a = azi * (pi / 180), e = ele * (pi / 180);
		var ce = cos(e), x = cos(a) * ce, y = sin(a) * ce, z = sin(e);
		var x2 = x * x, y2 = y * y, z2 = z * z;
		^switch (this.mode,
			\pair, {
				// along the line through the two speakers
				var along = [x, y, z].select { |c, i| this.prAxis[i].abs > 1e-9 }
					.collect { |c, i| c * this.prAxis.select { |v| v.abs > 1e-9 }[i] }.sum;
				[[1], [along * 2.sqrt]]
			},
			\ring, { [
				[1],
				[y, x] * 2.sqrt,
				[2 * x * y, x2 - y2] * 2.sqrt,
				[y * ((3 * x2) - y2), x * (x2 - (3 * y2))] * 2.sqrt
			].keep(n + 1) },
			{ [
				[1],
				[y, z, x] * 3.sqrt,
				[15.sqrt * x * y, 15.sqrt * y * z, (5.sqrt / 2) * ((3 * z2) - 1),
					15.sqrt * x * z, (15.sqrt / 2) * (x2 - y2)],
				[(35 / 8).sqrt * y * ((3 * x2) - y2), 105.sqrt * x * y * z,
					(21 / 8).sqrt * y * ((5 * z2) - 1), (7.sqrt / 2) * z * ((5 * z2) - 3),
					(21 / 8).sqrt * x * ((5 * z2) - 1), (105.sqrt / 2) * z * (x2 - y2),
					(35 / 8).sqrt * x * (x2 - (3 * y2))]
			].keep(n + 1) }
		)
	}

	// a pair's line: from the first speaker to the second
	prAxis {
		^axis ?? {
			var d = this.class.prUnit(dirs[1][0], dirs[1][1]) - this.class.prUnit(dirs[0][0], dirs[0][1]);
			var len = d.squared.sum.sqrt;
			axis = if (len < 1e-6) { [0.0, 1.0, 0.0] } { d / len }
		}
	}

	prNumHarmonics { |n|
		^switch (this.mode, \pair, { 2 }, \ring, { (2 * n) + 1 }, { (n + 1).squared })
	}

	// what each order is multiplied by: [in-phase, max-rE, basic]
	prWeights { |n|
		var f = { |k| (1..k).product.max(1) }, flat = this.mode != \sphere;
		var leg = { |k, c| [1, c, ((3 * c * c) - 1) / 2, ((5 * c * c * c) - (3 * c)) / 2][k] };
		^[
			(0..n).collect { |k|
				if (flat) { f.(n).squared / (f.(n + k) * f.(n - k)) }
					{ (f.(n) * f.(n + 1)) / (f.(n + k + 1) * f.(n - k)) }
			},
			(0..n).collect { |k|
				if (flat) { cos(k * pi / ((2 * n) + 2)) }
					{ leg.(k, cos(137.9 * (pi / 180) / (n + 1.51))) }
			},
			1 ! (n + 1)
		]
	}

	// [matrix, exact]: the energy decoder at an order, and whether its
	// columns are orthonormal (then the squares sum to 1 by themselves). When
	// the harmonics sampled at the speakers do not span them - more
	// harmonics than speakers, speakers all in one tilted plane - the
	// nearest that can be made, and the gains are normalised after.
	prDecoder { |n|
		var cls = this.class, k = this.prNumHarmonics(n), l = dirs.size;
		var flat = this.mode == \ring;
		var yt = dirs.collect { |d| this.prHarmonics(d[0], if (flat) { 0 } { d[1] }, n).flat };
		var y = yt.flop, tall = l >= k, ok = tall, gram, g, d;
		gram = if (tall) { cls.prMul(y, yt) } { cls.prMul(yt, y) };
		g = cls.prInvSqrt(gram);
		if (g.isNil) {
			var ridge = gram.collect { |r, i| r[i] }.sum / gram.size * 1e-6;
			ok = false;
			g = cls.prInvSqrt(gram.collect { |r, i| r.copy.put(i, r[i] + ridge) });
		};
		if (g.isNil) { ^[nil, false] };
		d = if (tall) { cls.prMul(yt, g) } { cls.prMul(g, yt) };
		^[d * (k / d.flat.squared.sum.max(1e-30)).sqrt, ok]
	}

	prGains { |dec, isExact, n, azi, ele, focus|
		var w = this.prWeights(n), gains;
		var f1 = (focus * 2).clip(0, 1), f2 = ((focus * 2) - 1).clip(0, 1);
		var v = this.prHarmonics(azi, ele, n).collect { |group, k|
			if (k == 0) { group } {
				group * (w[0][k] + ((w[1][k] - w[0][k]) * f1) + ((w[2][k] - w[1][k]) * f2))
			}
		}.flat;
		if (dec.isNil) { ^(dirs.size.reciprocal.sqrt ! dirs.size) };
		v = v / v.squared.sum.sqrt;
		gains = dec.collect { |row| (row * v).sum };
		^if (isExact) { gains } { gains / gains.squared.sum.sqrt.max(1e-9) }
	}

	prOrderFor {
		var eles, lo, hi, grid;
		if (this.mode == \pair) { ^1 };
		eles = dirs.collect(_[1]);
		lo = eles.minItem;
		hi = eles.maxItem;
		grid = if (this.mode == \sphere) { (0..4).collect { |i| lo + ((hi - lo) * i / 4) } } { [0] }
			.collect { |e| (-180, -165 .. 165).collect { |a| [a, e] } }.flatten;
		^(maxOrder.min(3) .. 2).detect { |n|
			(this.prNumHarmonics(n) <= dirs.size) and: {
				var made = this.prDecoder(n);
				made[0].notNil and: { made[1] } and: {
					grid.every { |sd|
						this.measureGains(this.prGains(made[0], true, n, sd[0], sd[1], 0.5),
							sd[0], sd[1])[\err] <= maxError
					}
				}
			}
		} ? 1
	}

	// ---- VBAP --------------------------------------------------------

	// the azimuths in -180..180, sorted, with the widest gap between
	// neighbours: (azis:, gap:, after:) - after is the index, in azis, of
	// the speaker the gap ends at
	prArc {
		var azis = dirs.collect { |d| d[0].wrap(-180.0, 180.0) }.sort;
		var gaps = azis.collect { |a, i| (azis.wrapAt(i + 1) - a).wrap(0.0, 360.0) };
		var widest;
		if (azis.size == 1) { gaps = [360.0] };
		// two speakers 180 apart: (a - b).wrap(0, 360) is right both ways
		widest = gaps.maxIndex;
		^(azis: azis, gap: gaps[widest], after: (widest + 1) % azis.size)
	}

	prVbapData {
		var cls = \VBAPSpeakerArray.asClass, data, arc, last;
		if (cls.isNil or: { dirs.size > 55 }) { ^nil };
		if (this.mode == \sphere) {
			data = try { cls.new(3, dirs.collect { |d| [d[0].asFloat, d[1].asFloat] }).getSetsAndMatrices };
			^if (data.notNil and: { data.size >= 23 }) { data }
		};
		data = try { cls.new(2, dirs.collect { |d| d[0].wrap(-180.0, 180.0) }).getSetsAndMatrices };
		if (data.isNil or: { data.size < 8 }) { ^nil };
		// an arc of three or more: VBAPSpeakerArray always joins the last
		// speaker round to the first (it compares radians with degrees), and
		// that pair is the last one written
		arc = this.prArc;
		if (dirs.size >= 3 and: { arc[\gap] > 170 } and: { arc[\after] == 0 }) {
			var azis = dirs.collect { |d| d[0].wrap(-180.0, 180.0) };
			last = data.keep(-6).keep(2).asInteger - 1;
			if ((azis[last[0]] == arc[\azis].last) and: { azis[last[1]] == arc[\azis].first }) {
				data = data.drop(-6)
			};
		};
		^if (data.size >= 8) { data }
	}

	prRanges {
		var arc, eles;
		if (rangesDone) { ^this };
		rangesDone = true;
		if (this.mode == \sphere) {
			eles = dirs.collect(_[1]);
			eleRange = [
				if (this.vbapCovers(0, -90)) { -90 } { eles.minItem },
				if (this.vbapCovers(0, 90)) { 90 } { eles.maxItem }
			];
		} {
			arc = this.prArc;
			if (arc[\gap] > 170) {
				var from = arc[\azis][arc[\after]];
				aziRange = [from, from + (360 - arc[\gap])];
			}
		}
	}

	// ---- the table ---------------------------------------------------

	*prNew { |name, dirs, lfe, label, builtin|
		^super.newCopyArgs(name.asSymbol, this.prDirs(dirs), lfe, (label ? name).asString, builtin)
	}

	*prPut { |setup|
		if (table.at(setup.name).isNil) { sequence = sequence.add(setup.name) };
		table.put(setup.name, setup);
	}

	// every direction as [azi, ele], in a copy of our own
	*prDirs { |dirs|
		^dirs.asArray.collect { |d| if (d.isNumber) { [d, 0] } { [d[0], d[1] ? 0] } }
	}

	// what is wrong with these speakers, or nil
	*prProblem { |dirs, lfe|
		var ok = { |x| x.isNumber and: { x.isNaN.not } and: { x.abs != inf } };
		if (dirs.isKindOf(SequenceableCollection).not or: { dirs.isString }) {
			^"the speakers are an array of [azimuth, elevation]"
		};
		if (dirs.size < 2) { ^"a setup has two speakers at least" };
		if (dirs.size > 55) { ^"a setup has 55 speakers at most, this has %".format(dirs.size) };
		dirs.do { |d, i|
			var fine = ok.(d) or: {
				d.isKindOf(SequenceableCollection) and: { d.isString.not }
					and: { d.size.inclusivelyBetween(1, 2) } and: { d.every(ok) }
			};
			if (fine.not) { ^"speaker % is %, not [azimuth, elevation] in degrees".format(i + 1, d) };
			if (d.isNumber.not and: { (d[1] ? 0).abs > 90 }) {
				^"speaker % has an elevation of % - it is between -90 and 90".format(i + 1, d[1])
			};
		};
		if (lfe.notNil and: { lfe.isInteger.not or: { lfe.inclusivelyBetween(0, dirs.size).not } }) {
			^"the sub is at %, not an output channel from 0 to %".format(lfe, dirs.size)
		};
		^nil
	}

	*prUnit { |azi, ele|
		var a = azi * (pi / 180), e = ele * (pi / 180);
		^[cos(a) * cos(e), sin(a) * cos(e), sin(e)]
	}

	*prSym { |sym|
		var str = sym.asString;
		^if (str.notEmpty and: { str[0].isAlpha }
			and: { str.every { |c| c.isAlphaNum or: { c == $_ } } }) {
			"\\" ++ str
		} {
			sym.asSymbol.asCompileString
		}
	}

	// ---- small matrices, as arrays of rows ----------------------------

	*prMul { |a, b|
		var bt = b.flop;
		^a.collect { |r| bt.collect { |c| (r * c).sum } }
	}

	// Gauss-Jordan with partial pivoting; nil when singular
	*prInv { |m, eps = 1e-9|
		var n = m.size, big = m.flat.abs.maxItem.max(1e-30);
		var a = m.collect { |r, i|
			r.asArray.copy ++ Array.fill(n, { |j| (i == j).binaryValue.asFloat })
		};
		n.do { |c|
			var p = (c .. n - 1).maxItem { |r| a[r][c].abs };
			if (a[p][c].abs < (eps * big)) { ^nil };
			a.swap(c, p);
			a[c] = a[c] / a[c][c];
			n.do { |r| if (r != c) { a[r] = a[r] - (a[c] * a[r][c]) } };
		};
		^a.collect(_.drop(n))
	}

	// the inverse square root of a symmetric positive matrix
	// (Denman-Beavers); nil when singular
	*prInvSqrt { |m, iters = 40|
		var y = m, z = m.collect { |r, i| r.collect { |v, j| (i == j).binaryValue.asFloat } };
		iters.do {
			var yi = this.prInv(y), zi = this.prInv(z), y2, z2, moved;
			if (yi.isNil or: { zi.isNil }) { ^nil };
			y2 = (y + zi) / 2;
			z2 = (z + yi) / 2;
			moved = (z2 - z).flat.abs.maxItem;
			y = y2;
			z = z2;
			if (moved < (1e-14 * z.flat.abs.maxItem.max(1e-30))) { ^z };
		};
		^z
	}

	// ---- the setups that come with the rack --------------------------

	// [name, dirs, lfe, label]
	*prBuiltins {
		var flat = { |azis| azis.collect { |a| [a, 0] } };
		var ring = { |n, ele = 0| n.collect { |i| [(((i - 0.5) * 360 / n) + 180).wrap(0.0, 360.0) - 180, ele] } };
		var whole = { |dirs| dirs.collect { |d| d.collect { |x| if (x == x.round) { x.asInteger } { x } } } };
		var octo = ring.(8);
		var five = flat.([-30, 30, 0, -110, 110]);
		var seven = flat.([-30, 30, 0, -90, 90, -135, 135]);
		var corner = atan(1 / 2.sqrt) * 180 / pi;    // 35.26: the corners of a cube
		var quad = { |ele| [[-45, ele], [45, ele], [-135, ele], [135, ele]] };
		var around = { |ele| [[-45, ele], [45, ele], [135, ele], [-135, ele]] };
		^[
			[\stereo, flat.([-30, 30]), nil, "stereo"],
			// left right, front then back
			[\quad, quad.(0), nil, "quad"],
			// clockwise from front left
			[\quadring, around.(0), nil, "quad ring"],
			// L R C Ls Rs
			['5.0', five, nil, "5.0"],
			// L R C LFE Ls Rs
			['5.1', five, 3, "5.1"],
			// L R C LFE Lss Rss Lrs Rrs
			['7.1', seven, 3, "7.1"],
			// then four above, 30 up
			['5.1.4', five ++ [[-30, 30], [30, 30], [-110, 30], [110, 30]], 3, "5.1.4"],
			['7.1.4', seven ++ quad.(30), 3, "7.1.4"],
			// clockwise from front left, the front between two speakers
			[\hex, ring.(6), nil, "hexagon"],
			[\octo, octo, nil, "octo"],
			// the same eight as left right pairs, front to back
			[\octopairs, flat.([-22.5, 22.5, -67.5, 67.5, -112.5, 112.5, -157.5, 157.5]), nil, "octo pairs"],
			// clockwise from a speaker straight ahead
			[\octofront, flat.([0, 45, 90, 135, 180, -135, -90, -45]), nil, "octo centre"],
			[\ring16, ring.(16), nil, "ring 16"],
			// the lower four, then the upper four, each as quad
			[\cube, quad.(corner.neg) ++ quad.(corner), nil, "cube"],
			// ring by ring from the floor, clockwise from front left, the top last
			[\dome13, octo ++ around.(45) ++ [[0, 90]], nil, "dome 8+4+1"],
			[\dome24, ring.(12) ++ ring.(8, 30) ++ around.(60), nil, "dome 12+8+4"]
		].collect { |b| [b[0], whole.(b[1]), b[2], b[3]] }
	}
}
