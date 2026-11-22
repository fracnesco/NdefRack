// quantize - a pitch held to a scale
//
// in is pitch in octaves, the way oct inputs take it; out is the nearest
// note of the scale, in octaves too. scale: 0 chromatic, 1 major, 2 minor,
// 3 major pentatonic, 4 minor pentatonic, 5 dorian, 6 whole tone.
//
// With voices, a quantizer for each, every control a value per voice: a
// chord through it keeps every note in the scale.
(
	category: \sequencing,
	help: "snaps a pitch in octaves to the nearest note of a scale (0 chromatic, 1 major, 2 minor, 3/4 pentatonic, 5 dorian, 6 whole tone); root in semitones; with voices, one per voice",
	about: "Snaps a pitch to the nearest note of a scale. Patch a sequencer, lfo or random source into in and the result into a wave's oct, and wandering pitches become melodies in key.",
	params: (
		in: "the pitch in octaves - from a sequencer, lfo, random source or pitch",
		root: "the key: the semitone the scale starts on, 0 the note at 0",
		scale: "the scale: chromatic, major, minor, major or minor pentatonic, dorian, whole tone"
	),
	labels: (scale: ["chromatic", "major", "minor", "major pentatonic", "minor pentatonic", "dorian", "whole tone"]),
	poly: { |n| {
		var scales = [
			(0..11), [0, 2, 4, 5, 7, 9, 11], [0, 2, 3, 5, 7, 8, 10], [0, 2, 4, 7, 9],
			[0, 3, 5, 7, 10], [0, 2, 3, 5, 7, 9, 10], [0, 2, 4, 6, 8, 10]
		];
		var root = \root.kr(0 ! n, spec: [0, 11, \lin, 1]).round;
		var semis = (\in.kr(0 ! n, spec: [-4, 4]) * 12) - root;
		var oct = (semis / 12).floor;
		var within = semis - (oct * 12);
		// for every scale the nearest degree, the octave above counting too
		var nearest = scales.collect { |degrees|
			var best = degrees[0], dist = (within - degrees[0]).abs;
			(degrees[1..] ++ [12]).do { |d|
				var closer = (within - d).abs < dist;
				best = Select.kr(closer, [best, d]);
				dist = dist.min((within - d).abs);
			};
			best
		};
		var note = Select.kr(\scale.kr(1 ! n, spec: [0, 6, \lin, 1]).round, nearest);
		((oct * 12) + note + root) / 12
	} }
)
