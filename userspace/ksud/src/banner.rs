use std::fmt::Write as _;

/// The banner printed by the flashing commands.
///
/// It used to come from `figlet-rs`, which exists only to draw ASCII art and pulls in a second,
/// unmaintained copy of the `zip` crate (0.6) that carries open advisories. The logo is static, so
/// it is written out here and the dependency is gone.
const LOGO: &str = r"
 _   _                       ____  _
| | | |_   _ _ __   ___ _ __/ ___|| | | |
| |_| | | | | '_ \ / _ \ '__\___ \| | | |
|  _  | |_| | |_) |  __/ |   ___) | |_| |
|_| |_|\__, | .__/ \___|_|  |____/ \___/
       |___/|_|
";

pub fn print_banner() -> String {
    let mut out = String::with_capacity(LOGO.len());
    let _ = write!(out, "{LOGO}");
    out
}
