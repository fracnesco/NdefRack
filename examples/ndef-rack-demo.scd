// =====================================================================
// NdefRack - a walkthrough, one block at a time
//
// Evaluate each ( ... ) block, or each single line, in turn with the
// cursor inside it (ctrl/cmd + enter). This is an editor document, not a
// file to .load.
// =====================================================================

// ---- 1. boot, and open the rack
s.boot;

NdefRack.gui;

// The rack is empty - or it shows whatever Ndefs you already had. Leave the
// window where you can see it while you evaluate the rest. Hover over
// anything in it: the bar along the bottom says what it is and what it does.


// ---- 2. any Ndef is a module
(
Ndef(\pad, {
	var sig = Saw.ar(\freq.kr(110, spec: [40, 400, \exp]) * [1, 1.01]);
	RLPF.ar(sig, \cutoff.kr(1200), \rq.kr(0.3)) * \amp.kr(0.1)
});
)

// A module called pad, ar 2, with a bar for every control. Drag them left and
// right (shift for fine); press its play button. Then redefine it - one more control - and watch the module
// follow:

(
Ndef(\pad, {
	var sig = Saw.ar(\freq.kr(110, spec: [40, 400, \exp]) * [1, 1.01, 0.995 * \spread.kr(1, spec: [0.98, 1.02])]);
	RLPF.ar(sig.sum ! 2 * 0.5, \cutoff.kr(1200), \rq.kr(0.3)) * \amp.kr(0.1)
});
)

NdefRack.at(\pad).paramNames;
NdefRackSpec.origin(Ndef(\pad), \cutoff);     // -> guess: the name says frequency


// ---- 3. a cable is a mapping
(
Ndef(\sweep, NdefRackLib(\lfo)).set(\rate, 0.1, \lo, 300, \hi, 3000);
Ndef(\pad).set(\cutoff, Ndef(\sweep));        // plain JITLib - and a cable in the rack
)

// The cutoff bar takes no drag now and shows what the cable brings in. In the
// window, pick the cable up at pad's cutoff port and drop it on the empty rack
// to unplug it, or right click the cable on the empty rack - or:

NdefRack.disconnect(\pad, \cutoff);          // the bar is back at 1200
NdefRack.connect(\sweep, \pad, \cutoff);     // and plugged again
NdefRack.cables;

Ndef(\pad).stop;


// ---- 4. the harmonic oscillator, and eight lfos for its eight levels
(
NdefRack.make(\verbos, \osc);
NdefRack.make(\lfo8, \lfo8).set(\rate, 0.15);
NdefRack.connect(\lfo8, \osc, \levels);
NdefRack.play(\osc);
)

// The partials take turns: lfo8's eight phases, one per level. In the window,
// narrow width and move scan - the window over the harmonics.

Ndef(\osc).set(\width, 0.2, \scan, 0.5);
Ndef(\lfo8).set(\spread, 0);                 // all eight together
Ndef(\lfo8).set(\spread, 1, \shape, 3);      // apart again, square


// ---- 5. a random walk, and a Turing Machine to play the tune
(
NdefRack.make(\walk, \walk).set(\lo, 0, \hi, 1, \rate, 3, \smooth, 0.3);
NdefRack.connect(\walk, \osc, \scan);        // the window wanders

NdefRack.make(\clock, \clk).set(\bpm, 104);
NdefRack.make(\turing, \tm).set(\rate, 0, \length, 8, \change, 0.08, \hi, 2);
NdefRack.make(\quantize, \q).set(\scale, 4);  // minor pentatonic
NdefRack.connect(\clk, \tm, \clock);         // the clock's beat, its first channel
NdefRack.connect(\tm, \q, \in);              // the machine's value, its first channel
NdefRack.connect(\q, \osc, \oct);
)

// change near 0 locks the loop; turn it up and the tune forgets itself.
Ndef(\tm).set(\change, 0);
Ndef(\tm).set(\change, 0.5);
Ndef(\tm).set(\change, 1);                   // locked again, at twice the length

NdefRack.stop(\osc);


// ---- 6. a voice: oscillator, filter, envelopes, amplifier
(
NdefRack.make(\wave, \wave).set(\shape, 2, \freq, 110);    // a saw
NdefRack.make(\ladder, \filter).set(\freq, 300, \res, 0.45);
NdefRack.make(\amp, \amp);
NdefRack.make(\adsr, \env).set(\attack, 0.003, \decay, 0.25, \sustain, 0.2, \release, 0.2);
NdefRack.make(\adsr, \fenv).set(\decay, 0.15, \sustain, 0, \hi, 3);

// the machine's second channel is its gate: pick takes it out
NdefRack.make(\pick, \gate).set(\chan, 1);
NdefRack.connect(\tm, \gate, \in);

NdefRack.connect(\gate, \env, \trig);
NdefRack.connect(\gate, \fenv, \trig);
NdefRack.connect(\q, \wave, \oct);
NdefRack.connect(\wave, \filter, \in);
NdefRack.connect(\fenv, \filter, \oct);         // three octaves of filter, every note
NdefRack.connect(\env, \amp, \gain);
NdefRack.connect(\filter, \amp, \in);
NdefRack.play(\amp);
)


// ---- 7. a mixer, an echo and a room
(
NdefRack.stop(\amp);
NdefRack.make(\delay, \dly).set(\time, 0.43, \fb, 0.5, \tone, 2500, \mix, 0.3);
NdefRack.make(\mixer, \mix).set(\levels, [1, 0.35, 0, 0]);
NdefRack.make(\reverb, \verb).set(\size, 0.85, \mix, 0.3);
NdefRack.connect(\amp, \dly, \in);
NdefRack.connect(\dly, \mix, \in1);
NdefRack.connect(\osc, \mix, \in2);
NdefRack.connect(\mix, \verb, \in);          // stereo into stereo
NdefRack.play(\verb);
)

// press arrange in the window: the modules in the order the signal goes.


// ---- 8. look at it: a scope and a spectrum
(
NdefRack.make(\scope, \sc).set(\time, 0.01, \gain, 1.5);
NdefRack.make(\spectrum, \sp);
NdefRack.connect(\filter, \sc, \in);            // the filter, before the amp
NdefRack.connect(\mix, \sp, \in);            // everything, before the room
)

// The scope draws what comes into it, starting each time channel 1 rises
// through zero; time and gain on it set the picture and do nothing to the
// sound. The spectrum's peaks fall back slowly, so they can be read. Both
// pass the signal on from their output, so they can sit in a chain.


// ---- 9. select, copy, paste, delete
// In the window: click a title to select a module, shift-click to add more,
// or drag a band on the empty rack. Drag one of them and they all move.
// cmd-c copies, cmd-v pastes where the mouse is - with the cables between
// the copies, not the ones to the rest. cmd-d duplicates, backspace deletes,
// cmd-z brings the last delete back. The x on a module deletes it too.
// The same from code:

NdefRack.copy([\wave, \filter, \amp]);
NdefRack.paste(Point(40, 700));              // wave_2, filter_2, amp_2 - wired to each other only

NdefRack.removeAll([\wave_2, \filter_2, \amp_2]);
NdefRack.undoRemove;                         // back, cables and all
NdefRack.duplicate([\env]);                  // env_2: its gate cable came from outside, so not that


// ---- 10. the patch as code, and saved
NdefRack.patchDocument;                      // a new document, as patch as code in the window does
NdefRack.postPatch;                          // or in the post window

NdefRack.savePatch("demo");
NdefRack.patches;

// take a cable out and load it back
NdefRack.disconnect(\mix, \in2);
NdefRack.loadPatch("demo");


// ---- 11. a module of your own in the library
(
NdefRackLib.add(\drone, {
	Saw.ar(\freq.kr(55, spec: \freq) * [1, 1.003, 0.997]).sum * \amp.kr(0.1, spec: \amp)
}, \oscillators, "three detuned saws");
)

NdefRack.make(\drone);                       // it is in the + module menu too
NdefRack.connect(\drone, \mix, \in3);
Ndef(\mix).set(\levels, [1, 0.35, 0.5, 0]);

// or a folder of files shaped like the ones in modules/:
// NdefRackLib.addFolder("~/my-modules");


// ---- 12. voices: one chain, a chord played through it
// in the window, right click a module for its voices
(
NdefRack.make(\chord, \chd, 4);              // a pitch per voice: a major chord to start
NdefRack.make(\wave, \poly, 4);               // four oscillators in one module
NdefRack.make(\lpf, \polyf, 4);              // four filters
NdefRack.make(\lfo, \drift);                 // one lfo, for every voice
NdefRack.make(\splay, \wide);                // the voices onto a stereo pair
NdefRack.set(\chd, \oct, -1);               // an octave down
NdefRack.set(\poly, \shape, 2);              // saws: NdefRack.set sets every voice
NdefRack.set(\poly, \detune, 0.2);
NdefRack.set(\poly, \amp, 0.1);
NdefRack.set(\polyf, \freq, 700);
NdefRack.set(\drift, \rate, 0.1);
NdefRack.connect(\chd, \poly, \oct);        // four into four: voice to voice
NdefRack.connect(\poly, \polyf, \in);
NdefRack.connect(\drift, \polyf, \oct);      // one into four: into every voice
NdefRack.connect(\polyf, \wide, \in);        // splay takes the cable's four voices
NdefRack.play(\wide);
)

// six voices - the chord's notes again an octave up. Every cable stays
// patched, and splay follows the filter that feeds it.
[\chd, \poly, \polyf].do { |k| NdefRack.setVoices(k, 6) };
NdefRack.voices(\wide);                      // -> 6
NdefRack.set(\chd, \shape, 4);               // a major seventh


// ---- 13. listening: the rack hears itself
// The listening modules turn a sound into control signals. A pulsar plays a
// rhythm - three pulses and a rest, over and over - onsets hears each pulse
// and plucks a string with it, the tune from 5 plays the string, and how
// loud the string rings moves the pulsars' formant up.
NdefRack.stop(\wide);                        // the chord off, to hear this
(
NdefRack.make(\pulsar, \pls).set(\freq, 6, \formant, 1500, \cycles, 3, \window, 2, \burst, 3, \rest, 1);
NdefRack.make(\onsets, \ears);
NdefRack.make(\pluck, \str).set(\decay, 1.5, \amp, 0.3);
NdefRack.make(\follower, \fol).set(\floor, -40, \hi, 2);
NdefRack.connect(\pls, \ears, \in);
NdefRack.connect(\ears, \str, \trig);        // a gate for every pulse it hears
NdefRack.connect(\q, \str, \oct);            // the tune from 5
NdefRack.connect(\str, \fol, \in);
NdefRack.connect(\fol, \pls, \shift);        // the louder the string, the higher the formant
NdefRack.connect(\str, \mix, \in4);
NdefRack.set(\mix, \levels, [1, 0.35, 0.5, 0.7]);
NdefRack.play(\pls);
)

// Rest the mouse on the cable from pls to ears: the pulses, as they go in.
// With a microphone - and headphones - listen to yourself instead: the string
// plays on your attacks, and a wave sings the note you sing.
(
NdefRack.make(\input, \mic);
NdefRack.make(\pitch, \tracker);
NdefRack.make(\wave, \echo).set(\amp, 0.1);
NdefRack.connect(\mic, \ears, \in);
NdefRack.connect(\mic, \tracker, \in);
NdefRack.connect(\tracker, \echo, \oct);     // octaves from ref, 220 Hz - the wave's freq
NdefRack.play(\echo);
)


// ---- 14. a bus BusManager knows
// With BusManager installed, the bus module reads one of its named buses: here
// the pulsars of 13 go onto a bus called pulses instead of the speakers, and
// come back into the rack from it - into a scope half a second wide. Without
// BusManager, the module still reads a bus by index - its drop-down, a bus
// index - but this block needs the name; skip it.
(
BusManager.add(\pulses, 1);
Ndef(\pls).play(out: BusManager.index(\pulses), numChannels: 1);
NdefRack.make(\bus, bus: \pulses);           // Ndef(\pulses): what is on the bus
NdefRack.make(\scope, \look).set(\time, 0.5);
NdefRack.connect(\pulses, \look, \in);
)

// click the pulses module's drop-down: any other bus BusManager knows, or a bus index

// The other way round, a send writes to a bus: what comes in at its in is added
// to the bus and passed on at its output. Here the echo of 13 goes onto a bus
// called echoes - whether or not echo plays - and the bus module above moves
// over to read it: the scope now shows the echo.
(
BusManager.add(\echoes, 1);
NdefRack.make(\send, \tap, bus: \echoes);    // Ndef(\tap): its drop-down says echoes
NdefRack.connect(\echo, \tap, \in);
NdefRack.setBus(\pulses, \echoes);           // the same module, another bus
)


// ---- 15. among speakers
// vbap places a sound among the speakers of a setup - here four, a quad - and
// a ramp into azi carries it round once every eight seconds. The module has a
// channel per speaker; played on a server with two outputs it is folded onto
// the stereo pair, so you hear the front pair as left and right and the back
// pair between them. With four outputs or more each speaker gets its own.
(
NdefRack.make(\wave, \tone).set(\amp, 0.1, \shape, 2);
NdefRack.make(\vbap, \around, speakers: \quad);
Ndef(\circle, { LFSaw.kr(\rate.kr(0.125, spec: [0.01, 4, \exp])) });   // -1 to 1: once round
NdefRack.connect(\tone, \around, \in);
NdefRack.connect(\circle, \around, \azi);
NdefRack.play(\around);
)

// the drop-down under the title has the other setups - or, from here:
NdefRack.setSpeakers(\around, \octo);
// ambi has the same controls, and gives every speaker a share
NdefRackSpeakers.names;


// ---- 16. tidying up
[\verb, \wide, \pls, \echo, \around].do { |k| NdefRack.stop(k) };

// every module out of the rack, and every Ndef cleared with it
NdefRack.removeAll(NdefRack.names);
