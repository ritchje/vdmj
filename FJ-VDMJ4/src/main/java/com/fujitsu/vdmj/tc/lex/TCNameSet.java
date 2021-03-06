/*******************************************************************************
 *
 *	Copyright (c) 2017 Fujitsu Services Ltd.
 *
 *	Author: Nick Battle
 *
 *	This file is part of VDMJ.
 *
 *	VDMJ is free software: you can redistribute it and/or modify
 *	it under the terms of the GNU General Public License as published by
 *	the Free Software Foundation, either version 3 of the License, or
 *	(at your option) any later version.
 *
 *	VDMJ is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with VDMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

package com.fujitsu.vdmj.tc.lex;

import java.util.HashSet;

import com.fujitsu.vdmj.util.Utils;

public class TCNameSet extends HashSet<TCNameToken>
{
	private static final long serialVersionUID = 1L;

	public TCNameSet(TCNameToken name)
	{
		super();
		add(name);
	}

	public TCNameSet()
	{
		super();
	}

	@Override
	public String toString()
	{
		return Utils.setToString(this, ", ");
	}
}
