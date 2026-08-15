using System.Runtime.CompilerServices;

// The platform assemblies implement the internal client SPI; nobody else
// gets to see it.
[assembly: InternalsVisibleTo("RiseOn.NativeAdMob.Android")]
[assembly: InternalsVisibleTo("RiseOn.NativeAdMob.iOS")]
[assembly: InternalsVisibleTo("RiseOn.NativeAdMob.Editor")]
