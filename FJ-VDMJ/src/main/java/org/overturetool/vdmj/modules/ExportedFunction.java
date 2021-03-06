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

package org.overturetool.vdmj.modules;

import org.overturetool.vdmj.definitions.Definition;
import org.overturetool.vdmj.definitions.DefinitionList;
import org.overturetool.vdmj.definitions.ExplicitFunctionDefinition;
import org.overturetool.vdmj.definitions.ImplicitFunctionDefinition;
import org.overturetool.vdmj.definitions.LocalDefinition;
import org.overturetool.vdmj.lex.LexLocation;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.FlatCheckedEnvironment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.types.Type;
import org.overturetool.vdmj.util.Utils;

public class ExportedFunction extends Export
{
	private static final long serialVersionUID = 1L;
	public final LexNameList nameList;
	public final Type type;
	public final LexNameList typeParams;

	public ExportedFunction(LexLocation location, LexNameList nameList, Type type, LexNameList typeParams)
	{
		super(location);
		this.nameList = nameList;
		this.type = type;
		this.typeParams = typeParams;
	}

	@Override
	public String toString()
	{
		return "export function " + Utils.listToString(nameList) + ":" + type;
	}

	@Override
	public DefinitionList getDefinition(DefinitionList actualDefs)
	{
		DefinitionList list = new DefinitionList();

		for (LexNameToken name: nameList)
		{
			Definition def = actualDefs.findName(name, NameScope.NAMES);

			if (def != null)
			{
				list.add(def);
			}
		}

		return list;
	}

	@Override
	public DefinitionList getDefinition()
	{
		DefinitionList list = new DefinitionList();

		for (LexNameToken name: nameList)
		{
			list.add(new LocalDefinition(name.location, name, NameScope.GLOBAL, type));
		}

		return list;
	}

	@Override
	public void typeCheck(Environment env, DefinitionList actualDefs)
	{
		for (LexNameToken name: nameList)
		{
			Definition def = actualDefs.findName(name, NameScope.NAMES);

			if (def == null)
			{
				report(3183, "Exported function " + name + " not defined in module");
			}
			else
			{
				Type actualType = def.getType();

				if (typeParams != null)
				{
					if (def instanceof ExplicitFunctionDefinition)
					{
						ExplicitFunctionDefinition efd = (ExplicitFunctionDefinition)def;
						FlatCheckedEnvironment params =	new FlatCheckedEnvironment(
							efd.getTypeParamDefinitions(), env, NameScope.NAMES);

						Type resolved = type.typeResolve(params, null);
						
						if (efd.typeParams == null)
						{
							report(3352, "Exported " + name + " function has no type paramaters");
						}
						else if (!efd.typeParams.equals(typeParams))
						{
							report(3353, "Exported " + name + " function type parameters incorrect");
							detail2("Exported", typeParams, "Actual", efd.typeParams);
						}
						
						if (actualType != null && !actualType.toString().equals(resolved.toString()))
						{
							report(3184, "Exported " + name + " function type incorrect");
							detail2("Exported", resolved, "Actual", actualType);
						}
					}
					else if (def instanceof ImplicitFunctionDefinition)
					{
						ImplicitFunctionDefinition ifd = (ImplicitFunctionDefinition)def;
						FlatCheckedEnvironment params =	new FlatCheckedEnvironment(
							ifd.getTypeParamDefinitions(), env, NameScope.NAMES);

						Type resolved = type.typeResolve(params, null);
						
						if (ifd.typeParams == null)
						{
							report(3352, "Exported " + name + " function has no type paramaters");
						}
						else if (!ifd.typeParams.equals(typeParams))
						{
							report(3353, "Exported " + name + " function type parameters incorrect");
							detail2("Exported", typeParams, "Actual", ifd.typeParams);
						}
						
						if (actualType != null && !actualType.toString().equals(resolved.toString()))
						{
							report(3184, "Exported " + name + " function type incorrect");
							detail2("Exported", resolved, "Actual", actualType);
						}
					}
				}
				else
				{
					Type resolved = type.typeResolve(env, null);
					
					// if (actualType != null && !TypeComparator.compatible(resolved, actualType))
					if (actualType != null && !actualType.equals(resolved))
					{
						report(3184, "Exported " + name + " function type incorrect");
						detail2("Exported", resolved, "Actual", actualType);
					}
				}
			}
		}
	}
}
