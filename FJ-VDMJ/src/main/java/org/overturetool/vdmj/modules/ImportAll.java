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
import org.overturetool.vdmj.definitions.ImportedDefinition;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.typechecker.Environment;

public class ImportAll extends Import
{
	private static final long serialVersionUID = 1L;

	public ImportAll(LexNameToken name)
	{
		super(name, null);
	}

	@Override
	public DefinitionList getDefinitions(Module module)
	{
		from = module;

		if (from.exportdefs.isEmpty())
		{
			report(3190, "Import all from module with no exports?");
		}

		DefinitionList imported = new DefinitionList();

		for (Definition d: from.exportdefs)
		{
			Definition id = new ImportedDefinition(location, d);
			id.markUsed();	// So imports all is quiet
			imported.add(id);
		}

		return imported;	// The lot!
	}

	@Override
	public String toString()
	{
		return "import all";
	}

	@Override
	public void typeCheck(Environment env)
	{
		return;		// Implicitly OK.
	}
}
