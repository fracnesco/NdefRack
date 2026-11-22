// insert - an insert point on a bus: out to the effects, back in
//
// BusManager is looked up when a name is picked; without it, an index of
// your own still works.
//
// The output is what is on the bus; what comes back at in takes its place
// there, by mix. Patch insert -> any effects -> back into insert's in, and
// turn mix up: at 0, where it starts, the bus is left alone - so an insert
// with nothing patched back does not silence it - and at 1 the bus carries
// only what comes back. The bus is picked as bus's is, in the drop-down on
// the module's face or with NdefRack.make(\insert, bus: \drums); it works
// whether or not the module plays.
//
// Audio buses only: on a control bus it is a reader, its output what is on
// the bus, and it writes nothing - a control bus keeps its last value, and
// reading that back through the effects would be feedback.
//
// What was measured (2026-11-20):
// - It reads with In, what modules made before it have written in this
//   block, and replaces just that. So what writes the bus must be made
//   before the insert. Made after, it is not heard at the output and lands
//   on the bus untouched; the insert never feeds back on itself, whatever
//   the effects' gain.
// - Made again on the same bus - a new source, its effect changing width,
//   its bus re-put - the old synth stops reading and writing at once and
//   the new one fades in, both in the output and on the bus: a dip, never
//   two of them at a time. Two at a time made a loop through the effects
//   for the length of the fade - 98 on the bus for 4 at an effect gain of 8.
// - A mono effect on a 2-channel bus comes back on the first channel only:
//   at mix 1 the second is silent. Use a stereo effect, or one insert per
//   channel.
// - A module playing onto the bus (the play button, Ndef.play(out:)) goes to
//   the end each time it is started: play first, then make the insert.
// - The output is on time. What comes back is one block late (64 samples),
//   wherever the effects were made - two blocks through two effects made
//   before the insert in reverse order. At a mix between 0 and 1 the bus
//   has both, a block apart: a comb, its first notch near 345 Hz at 44.1k.
//   mix 1 is the clean setting; set the effect's own dry/wet for less.
(
	category: \buses,
	help: "an insert on an audio bus: the output is what is on it, what comes back at in replaces it by mix (0 leaves the bus alone, 1 is the clean setting - the return is a block late); what writes the bus must be made before it - pick the bus in the drop-down",
	about: "An insert point on an audio bus: its output is what is on the bus, and what you patch back into in replaces it, by mix. Patch insert into effects and back into its in, then turn mix up. Make it after whatever writes the bus.",
	params: (
		in: "the return: patch the last effect of the chain back here",
		mix: "how much of the return replaces the bus: 0 leaves the bus alone, 1 is only the return (the clean setting)"
	),
	writes: \audio,
	reads: true,
	bus: { |index, channels, rate| {
		case
		{ index.isNil } { \in.ar(0); \mix.kr(0, spec: [0, 1]); DC.ar(0) }
		{ rate == \control } { In.kr(index, channels) }
		{
			var back = \in.ar(0 ! channels);
			var mix = \mix.kr(0, spec: [0, 1]);
			var env = EnvGate.new(i_level: 0, doneAction: 2, curve: \sin);
			// released, it is gone at once: a synth on its way out beside the
			// new one would read the other's return - a loop through the effects
			var on = \gate.kr(1) > 0;
			// In, not InFeedback: only what was written earlier in this block,
			// never its own return
			var dry = In.ar(index, channels);
			// <! makes the write wait for the read: without it the SynthDef
			// may put the XOut before the In, and the insert reads itself
			XOut.ar(index, mix * env * on, back <! dry);
			dry * env * on
		}
	} }
)
