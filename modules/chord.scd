// chord - a chord, one pitch per voice
//
// oct is the root in octaves, the way oct inputs take pitch; out comes
// one pitch per voice, in octaves too - patch it into the oct of a wave
// with as many voices. shape: 0 unison, 1 major, 2 minor, 3 sus4, 4 major
// seventh, 5 minor seventh, 6 dominant seventh, 7 fifths. Voices past the
// chord's notes take them again, an octave up.
(
	category: \sequencing,
	help: "a chord, one pitch in octaves per voice, from the root at oct: shape 0 unison, 1 major, 2 minor, 3 sus4, 4 maj7, 5 min7, 6 dom7, 7 fifths",
	about: "Makes a chord from one root pitch: each voice gets one note, in octaves. Patch it into the oct of an oscillator with as many voices to play the chord; voices past the chord's notes repeat them an octave up.",
	params: (
		shape: "the chord: unison, major, minor, sus4, maj7, min7, dom7, fifths",
		oct: "the root pitch in octaves - patch a sequencer or a pitch here"
	),
	labels: (shape: ["unison", "major", "minor", "sus4", "maj7", "min7", "dom7", "fifths"]),
	poly: { |n| {
		var shapes = [[0], [0, 4, 7], [0, 3, 7], [0, 5, 7], [0, 4, 7, 11], [0, 3, 7, 10], [0, 4, 7, 10], [0, 7]];
		var shape = \shape.kr(1, spec: [0, 7, \lin, 1]).round;
		var notes = n.collect { |i|
			Select.kr(shape, shapes.collect { |sh| sh.wrapAt(i) + (12 * (i div: sh.size)) })
		};
		\oct.kr(0, spec: [-4, 4]) + (notes / 12)
	} }
)
