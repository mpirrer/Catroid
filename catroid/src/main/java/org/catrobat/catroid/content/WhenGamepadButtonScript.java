/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2018 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.content;

import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.content.bricks.ScriptBrick;
import org.catrobat.catroid.content.bricks.WhenGamepadButtonBrick;
import org.catrobat.catroid.content.eventids.EventId;
import org.catrobat.catroid.content.eventids.GamepadEventId;

public class WhenGamepadButtonScript extends Script implements EventScript {

	private static final long serialVersionUID = 1L;
	private String action;

	public WhenGamepadButtonScript(String action) {
		this.action = action;
	}

	@Override
	protected Object readResolve() {
		super.readResolve();
		return this;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getAction() {
		return action;
	}

	@Override
	public ScriptBrick getScriptBrick() {
		if (brick == null) {
			brick = new WhenGamepadButtonBrick(this);
		}
		return brick;
	}

	@Override
	public int getRequiredResources() {
		return Brick.CAST_REQUIRED | super.getRequiredResources();
	}

	@Override
	public Script clone() throws CloneNotSupportedException {
		WhenGamepadButtonScript clone = new WhenGamepadButtonScript(action);
		clone.getBrickList().addAll(cloneBrickList());
		return clone;
	}

	@Override
	public EventId createEventId(Sprite sprite) {
		return new GamepadEventId(action);
	}
}
