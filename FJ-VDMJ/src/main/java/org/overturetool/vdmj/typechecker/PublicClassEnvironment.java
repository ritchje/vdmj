/*******************************************************************************
 *
 *	Copyright (c) 2008 Fujitsu Services Ltd.
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

package org.overturetool.vdmj.typechecker;

import org.overturetool.vdmj.definitions.ClassDefinition;
import org.overturetool.vdmj.definitions.ClassList;
import org.overturetool.vdmj.definitions.Definition;
import org.overturetool.vdmj.definitions.DefinitionSet;
import org.overturetool.vdmj.definitions.StateDefinition;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.lex.Token;

/**
 * Define the type checking environment for a set of classes, as observed
 * from the outside.
 */

public class PublicClassEnvironment extends Environment
{
	private final ClassList classes;

	public PublicClassEnvironment(ClassList classes)
	{
		super(null);
		this.classes = classes;
	}

	public PublicClassEnvironment(ClassList classes, Environment env)
	{
		super(env);
		this.classes = classes;
	}

	public PublicClassEnvironment(ClassDefinition one)
	{
		super(null);
		this.classes = new ClassList(one);
	}

	public PublicClassEnvironment(ClassDefinition one, Environment env)
	{
		super(env);
		this.classes = new ClassList(one);
	}

	@Override
	public Definition findName(LexNameToken name, NameScope scope)
	{
		Definition def = classes.findName(name, scope);

		if (def != null && def.isAccess(Token.PUBLIC) && !def.excluded)
		{
			return def;
		}

		return (outer == null) ? null : outer.findName(name, scope);
	}

	@Override
	public Definition findType(LexNameToken name, String fromModule)
	{
		Definition def = classes.findType(name);

		if (def != null && def.isAccess(Token.PUBLIC))
		{
			return def;
		}

		return (outer == null) ? null : outer.findType(name, null);
	}

	@Override
	public DefinitionSet findMatches(LexNameToken name)
	{
		DefinitionSet defs = classes.findMatches(name);

		if (outer != null)
		{
			defs.addAll(outer.findMatches(name));
		}

		return defs;
	}

	@Override
	public void unusedCheck()
	{
		classes.unusedCheck();
	}

	@Override
	public StateDefinition findStateDefinition()
	{
		return null;
	}

	@Override
	public boolean isVDMPP()
	{
		return true;
	}

	@Override
	public boolean isSystem()
	{
		return false;	// See PrivateClassEnvironment
	}

	@Override
	public ClassDefinition findClassDefinition()
	{
		return null;
	}

	@Override
	public boolean isStatic()
	{
		return false;
	}
}
