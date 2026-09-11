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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.hackli.guidesigner.render;

import java.nio.file.Path;

/**
 * Supplies images to the renderers, cached per back end.
 *
 * <p>The desktop editor reads PNGs straight into a {@code BufferedImage}; the
 * OpenGL editor uploads them once and keeps a GL texture id. The painter only
 * ever asks for a texture by path or by item id, so it stays back end agnostic.
 * Unknown images fall back to a deterministic placeholder colour, which is what
 * makes a design still readable when no Minecraft assets were extracted.</p>
 */
public interface TextureSource {
    /** A source that draws placeholders for everything. */
    TextureSource NONE = new TextureSource() {
        @Override public Object resolve(String reference) {
            return null;
        }
    };

    /**
     * @param reference a texture path ({@code item/diamond}, {@code block/stone},
     *                  {@code textures/item/diamond.png}) or a Meteor GUI icon name
     *                  ({@code reset}, {@code edit})
     * @return a back-end handle to draw, or {@code null} when unavailable (the
     *         painter then draws {@link MeteorTheme#TEXTURE_MISSING})
     */
    Object resolve(String reference);

    /** Optional: the file behind a reference, used for tooltips and diagnostics. */
    default Path fileOf(String reference) {
        return null;
    }
}
