/*
 * This file is part of Hackli GUI Studio (https://github.com/hankli22/hackli-gui-studio).
 * Copyright (C) 2026 hankli22 and contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.hackli.guidesigner.runtime;

/** A simple mutable rectangle in GUI coordinates. */
public class UiRect {
    public double x, y, w, h;

    public UiRect() {}

    public UiRect(double x, double y, double w, double h) {
        set(x, y, w, h);
    }

    public UiRect(UiRect other) {
        set(other);
    }

    public UiRect set(double x, double y, double w, double h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public UiRect set(UiRect other) {
        return set(other.x, other.y, other.w, other.h);
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    public boolean nearCorner(double px, double py, double radius) {
        boolean nearLeft = Math.abs(px - x) <= radius;
        boolean nearRight = Math.abs(px - (x + w)) <= radius;
        boolean nearTop = Math.abs(py - y) <= radius;
        boolean nearBottom = Math.abs(py - (y + h)) <= radius;

        return (nearLeft || nearRight) && (nearTop || nearBottom);
    }

    @Override
    public String toString() {
        return "UiRect(" + x + ", " + y + ", " + w + "x" + h + ")";
    }
}
