// Symlink this repository into the SuperCollider extensions folder.
// Evaluate the block below, then recompile the class library
// (Language -> Recompile Class Library, or ctrl/cmd + shift + L).
//
// This is the route for a clone you want to keep editing. If you would
// rather have it installed and updated for you, do not use this file -
// install it as a quark instead, from a fresh sclang:
//
//   Quarks.install("https://github.com/fracnesco/Ndef-rack.git");
//   thisProcess.recompile;
//
// One or the other, not both: two copies on the class path is a
// duplicate class error at compile time.
//
// ln -s is macOS and Linux. On Windows, use the quark, or copy this
// folder into Platform.userExtensionDir by hand - .openOS opens it.
//
// NdefRack depends on nothing but JITLib, which ships with SuperCollider.
// The few modules written for sc3-plugins UGens (dfm1, moogladder, svf)
// are left out of the library when sc3-plugins is not installed, and
// specs added with JITLibExtensions' addSpec are read when that quark is
// there. Neither is needed to compile.

(
var src = PathName(thisProcess.nowExecutingPath).pathOnly;
var dst = Platform.userExtensionDir +/+ "NdefRack";

// drop the trailing slash, ln -s does not like it
if (src.endsWith("/")) { src = src.drop(-1) };

case
{ File.exists(dst) } {
	("NdefRack: % already exists - remove it first if you want to relink."
		.format(dst)).warn;
}
{ true } {
	File.mkdir(Platform.userExtensionDir);
	("ln -s" + src.shellQuote + dst.shellQuote).unixCmd({ |exit|
		if (exit == 0) {
			"NdefRack: linked".postln;
			("   " ++ src).postln;
			("-> " ++ dst).postln;
			"".postln;
			"Now recompile the class library: ctrl/cmd + shift + L".postln;
		} {
			("NdefRack: linking failed with exit code" + exit).warn;
		};
	});
};
)


// ---------------------------------------------------------------- verify
(
var missing = [\NdefRack, \NdefRackModule, \NdefRackSpec, \NdefRackLib,
	\NdefRackGUI, \NdefRackModuleView, \NdefRackDisplay, \NdefRackHintBar]
	.select { |sym| sym.asClass.isNil };

if (missing.notEmpty) {
	("NdefRack is not compiled - % is missing."
		"\n  Recompile the class library, ctrl/cmd + shift + L, and check the"
		"\n  post window for compile errors.".format(missing.join(", "))).warn;
} {
	"NdefRack is installed.".postln;
	"  modules in the library: % (% skipped)".format(
		NdefRackLib.keys.size, NdefRackLib.skipped.size).postln;
	"  window: NdefRack.gui".postln;
	"  help:   NdefRack, then cmd+d".postln;
};
)


// ------------------------------------------------------------- uninstall
(
var link = Platform.userExtensionDir +/+ "NdefRack";
case
{ File.type(link) == \symlink } {
	File.delete(link);
	("Removed %."
		"\n  Recompile the class library to finish.".format(link)).postln;
}
{ File.type(link) == \not_found } {
	"Nothing to remove at %.".format(link).postln;
}
{ true } {
	"% is not a link, leaving it alone.".format(link).warn;
};
)
