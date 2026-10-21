#include "../../main/cpp/nds/input_mask.h"

// Independently specified DS register expectations, including both extended buttons.
constexpr unsigned int expected[] = {0xFFE, 0xFFD, 0xFFB, 0xFF7, 0xFEF, 0xFDF,
                                    0xFBF, 0xF7F, 0xEFF, 0xDFF, 0xBFF, 0x7FF};
constexpr bool all_buttons() {
    for (unsigned int bit = 0; bit < 12; ++bit)
        if (pk_nds_active_low_keys(pk_nds_pressed_keys(1u << bit)) != expected[bit]) return false;
    return true;
}
static_assert(all_buttons(), "All twelve DS controls must reach their hardware bit");
static_assert(pk_nds_active_low_keys(0) == 0xFFF, "Release all");
static_assert(pk_nds_active_low_keys(0xFFF) == 0, "Press all");
static_assert(pk_nds_pressed_keys(0xF000) == 0, "Reject bits outside DS registers");
static_assert(pk_nds_active_low_keys(0xC00) == 0x3FF, "X and Y must not become START/SELECT");
