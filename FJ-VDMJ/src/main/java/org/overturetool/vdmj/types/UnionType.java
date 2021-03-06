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

package org.overturetool.vdmj.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.overturetool.vdmj.definitions.AccessSpecifier;
import org.overturetool.vdmj.definitions.ClassDefinition;
import org.overturetool.vdmj.definitions.Definition;
import org.overturetool.vdmj.definitions.DefinitionList;
import org.overturetool.vdmj.definitions.LocalDefinition;
import org.overturetool.vdmj.definitions.TypeDefinition;
import org.overturetool.vdmj.lex.LexLocation;
import org.overturetool.vdmj.lex.LexNameList;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.lex.LexQuoteToken;
import org.overturetool.vdmj.lex.Token;
import org.overturetool.vdmj.runtime.Context;
import org.overturetool.vdmj.runtime.ValueException;
import org.overturetool.vdmj.typechecker.Environment;
import org.overturetool.vdmj.typechecker.NameScope;
import org.overturetool.vdmj.typechecker.TypeCheckException;
import org.overturetool.vdmj.util.Utils;
import org.overturetool.vdmj.values.ValueList;


public class UnionType extends Type
{
	private static final long serialVersionUID = 1L;

	public TypeSet types;

	private SetType setType = null;
	private SeqType seqType = null;
	private MapType mapType = null;
	private RecordType recType = null;
	private NumericType numType = null;
	private ProductType prodType = null;
	private FunctionType funcType = null;
	private OperationType opType = null;
	private ClassType classType = null;

	private boolean setDone = false;
	private boolean seqDone = false;
	private boolean mapDone = false;
	private boolean recDone = false;
	private boolean numDone = false;
	private boolean funDone = false;
	private boolean opDone = false;
	private boolean classDone = false;

	private int prodCard = -1;
	private boolean expanded = false;

	public UnionType(LexLocation location, Type a, Type b)
	{
		super(location);
		types = new TypeSet();
		types.add(a);
		types.add(b);
		expand();
	}

	public UnionType(LexLocation location, TypeSet types)
	{
		super(location);
		this.types = types;
		expand();
	}

	@Override
	public boolean narrowerThan(AccessSpecifier accessSpecifier)
	{
		for (Type t: types)
		{
			if (t.narrowerThan(accessSpecifier))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public Type isType(String typename, LexLocation from)
	{
		for (Type t: types)
		{
			Type rt = t.isType(typename, location);

			if (rt != null)
			{
				return rt;
			}
		}

		return null;
	}

	@Override
	public boolean isType(Class<? extends Type> typeclass, LexLocation from)
	{
		for (Type t: types)
		{
			if (t.isType(typeclass, location))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean isUnknown(LexLocation from)
	{
		for (Type t: types)
		{
			if (t.isUnknown(location))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean isVoid()
	{
		for (Type t: types)
		{
			if (!t.isVoid())
			{
				return false;		// NB. Only true if ALL void, not ANY void (see hasVoid)
			}
		}

		return true;
	}

	@Override
	public boolean hasVoid()
	{
		for (Type t: types)
		{
			if (t.isVoid())
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean isUnion(LexLocation from)
	{
		return true;
	}

	@Override
	public boolean isSeq(LexLocation from)
	{
		return getSeq() != null;
	}

	@Override
	public boolean isSet(LexLocation from)
	{
		return getSet() != null;
	}

	@Override
	public boolean isMap(LexLocation from)
	{
		return getMap() != null;
	}

	@Override
	public boolean isRecord(LexLocation from)
	{
		return getRecord() != null;
	}

	@Override
	public boolean isTag()
	{
		return false;
	}

	@Override
	public boolean isClass(Environment env)
	{
		return getClassType(env) != null;
	}

	@Override
	public boolean isNumeric(LexLocation from)
	{
		return getNumeric() != null;
	}

	@Override
	public boolean isProduct(LexLocation from)
	{
		return getProduct() != null;
	}

	@Override
	public boolean isProduct(int n, LexLocation from)
	{
		return getProduct(n) != null;
	}

	@Override
	public boolean isFunction(LexLocation from)
	{
		return getFunction() != null;
	}

	@Override
	public boolean isOperation(LexLocation from)
	{
		return getOperation() != null;
	}

	@Override
	public UnionType getUnion()
	{
		return this;
	}

	@Override
	public SeqType getSeq()
	{
		if (!seqDone)
		{
	   		seqDone = true;		// Mark early to avoid recursion.
	   		seqType = new UnknownType(location).getSeq();

	   		TypeSet set = new TypeSet();

    		for (Type t: types)
    		{
    			if (t.isSeq(location))
    			{
    				set.add(t.getSeq().seqof);
    			}
    		}

    		seqType = set.isEmpty() ? null :
    			new SeqType(location, set.getType(location));
 		}

		return seqType;
	}

	@Override
	public SetType getSet()
	{
		if (!setDone)
		{
    		setDone = true;		// Mark early to avoid recursion.
    		setType = new UnknownType(location).getSet();

    		TypeSet set = new TypeSet();

    		for (Type t: types)
    		{
    			if (t.isSet(location))
    			{
    				set.add(t.getSet().setof);
    			}
    		}

    		setType = set.isEmpty() ? null :
    			new SetType(location, set.getType(location));
		}

		return setType;
	}

	@Override
	public MapType getMap()
	{
		if (!mapDone)
		{
    		mapDone = true;		// Mark early to avoid recursion.
    		mapType = new UnknownType(location).getMap();

    		TypeSet from = new TypeSet();
    		TypeSet to = new TypeSet();

    		for (Type t: types)
    		{
    			if (t.isMap(location))
    			{
    				from.add(t.getMap().from);
    				to.add(t.getMap().to);
    			}
    		}

    		mapType = from.isEmpty() ? null :
    			new MapType(location, from.getType(location), to.getType(location));
		}

		return mapType;
	}

	@Override
	public RecordType getRecord()
	{
		if (!recDone)
		{
    		recDone = true;		// Mark early to avoid recursion.
    		recType = new UnknownType(location).getRecord();

    		// Build a record type with the common fields of the contained
    		// record types, making the field types the union of the original
    		// fields' types...

    		Map<String, TypeList> common = new HashMap<String, TypeList>();
    		int recordCount = 0;

    		for (Type t: types)
    		{
    			if (t.isRecord(location))
    			{
    				recordCount++;
    				
    				for (Field f: t.getRecord().fields)
    				{
    					TypeList current = common.get(f.tag);

    					if (current == null)
    					{
    						common.put(f.tag, new TypeList(f.type));
    					}
    					else
    					{
    						current.add(f.type);
    					}
    				}
    			}
    		}
    		
    		// If all fields were present in all records, the TypeLists will be the
    		// same size. But if not, the shorter ones have to have UnknownTypes added,
    		// because some of the records do not have that field.
    		
    		Map<String, TypeSet> typesets = new HashMap<String, TypeSet>();
    		
    		for (String field: common.keySet())
    		{
    			TypeList list = common.get(field);
    			
    			if (list.size() != recordCount)
    			{
    				// Both unknown and undefined types do not trigger isSubType, so we use
    				// an illegal quote type, <?>.
    				list.add(new QuoteType(new LexQuoteToken("?", location)));
    			}
    			
    			TypeSet set = new TypeSet();
    			set.addAll(list);
    			typesets.put(field, set);
    		}

    		List<Field> fields = new Vector<Field>();

    		for (String tag: typesets.keySet())
    		{
				LexNameToken tagname = new LexNameToken("?", tag, location);
				fields.add(new Field(tagname, tag, typesets.get(tag).getType(location), false));
    		}

    		recType = fields.isEmpty() ? null : new RecordType(location, fields);
		}

		return recType;
	}

	@Override
	public ClassType getClassType(Environment env)
	{
		if (!classDone)
		{
    		classDone = true;		// Mark early to avoid recursion.
    		classType = new UnknownType(location).getClassType(env);

    		// Build a class type with the common fields of the contained
    		// class types, making the field types the union of the original
    		// fields' types...

    		Map<LexNameToken, TypeSet> common = new HashMap<LexNameToken, TypeSet>();
    		Map<LexNameToken, AccessSpecifier> access = new HashMap<LexNameToken, AccessSpecifier>();
    		
    		// Derive the pseudoclass name for the combined union
    		String classString = "*union";	// NB, illegal class name
    		int count = 0;
    		ClassType found = null;

    		for (Type t: types)
    		{
    			if (t.isClass(env))
    			{
    				found = t.getClassType(env);
    				classString = classString + "_" + found.name.name;	// eg. "*union_A_B"
    				count++;
    			}
    		}
    		
    		if (count == 1)		// Only one class in union, so just return this one
    		{
    			classType = found;
    			return classType;
    		}
    		else if (count == 0)
    		{
    			classType = null;
    			return null;
    		}

    		LexNameToken classname = new LexNameToken("CLASS", classString, new LexLocation());
    		
    		for (Type t: types)
    		{
    			if (t.isClass(env))
    			{
    				ClassType ct = t.getClassType(env);

    				for (Definition f: ct.classdef.getDefinitions())
    				{
    					if (env != null && !ClassDefinition.isAccessible(env, f, false))
    					{
    						// Omit inaccessible fields
    						continue;
    					}
    					
    					// TypeSet current = common.get(f.name);
    					LexNameToken synthname = f.name.getModifiedName(classname.name);
    					TypeSet current = null;

    					for (LexNameToken n: common.keySet())
    					{
    						if (n.name.equals(synthname.name))
    						{
    							current = common.get(n);
    							break;
    						}
    					}

    					Type ftype = f.getType();

    					if (current == null)
    					{
    						common.put(synthname, new TypeSet(ftype));
    					}
    					else
    					{
    						current.add(ftype);
    					}

    					AccessSpecifier curracc = access.get(synthname);

    					if (curracc == null)
    					{
							AccessSpecifier acc = new AccessSpecifier(
								f.accessSpecifier.isStatic,
								f.accessSpecifier.isAsync,
								Token.PUBLIC,	// Guaranteed to be accessible
								f.accessSpecifier.isPure);

							access.put(synthname, acc);
    					}
    					else if (!curracc.isPure && f.accessSpecifier.isPure)
						{
							AccessSpecifier purified = new AccessSpecifier(
								f.accessSpecifier.isStatic,
								f.accessSpecifier.isAsync,
								Token.PUBLIC,
								curracc.isPure || f.accessSpecifier.isPure);

							access.put(synthname, purified);
						}
    				}
    			}
    		}

    		DefinitionList newdefs = new DefinitionList();

    		for (LexNameToken synthname: common.keySet())
    		{
    			Type ptype = common.get(synthname).getType(location);
    			LexNameToken newname = null;
    			
    			if (ptype.isOperation(location))
    			{
    				OperationType optype = ptype.getOperation();
    				OperationType newtype = new OperationType(optype.location, optype.parameters, optype.result);
    				newtype.setPure(access.get(synthname).isPure);
    				ptype = newtype;
    				newname = synthname.getModifiedName(optype.parameters);
    			}
    			else if (ptype.isFunction(location))
    			{
    				FunctionType ftype = ptype.getFunction();
    				newname = synthname.getModifiedName(ftype.parameters);
    			}
    			
    			Definition def = new LocalDefinition(synthname.location,
					(newname == null ? synthname : newname), NameScope.GLOBAL, ptype);
    			
    			def.setAccessSpecifier(access.get(synthname));
				newdefs.add(def);
    		}

    		classType = (classname == null) ? null :
    			new ClassType(location,
    				new ClassDefinition(classname,
    					new LexNameList(), newdefs));
		}

		return classType;
	}

	@Override
	public NumericType getNumeric()
	{
		if (!numDone)
		{
    		numDone = true;
			numType = new NaturalOneType(location);		// lightest default
			boolean found = false;

    		for (Type t: types)
    		{
    			if (t.isNumeric(location))
    			{
    				NumericType nt = t.getNumeric();

    				if (nt.getWeight() > numType.getWeight())
    				{
    					numType = nt;
    				}

    				found = true;
    			}
    		}

    		if (!found) numType = null;
		}

		return numType;
	}

	@Override
	public ProductType getProduct()
	{
		return getProduct(0);
	}

	@Override
	public ProductType getProduct(int n)
	{
		if (prodCard != n)
		{
    		prodCard = n;
    		prodType = new UnknownType(location).getProduct(n);

    		// Build a N-ary product type, making the types the union of the
    		// original N-ary products' types...

    		Map<Integer, TypeSet> result = new HashMap<Integer, TypeSet>();

    		for (Type t: types)
    		{
    			if ((n == 0 && t.isProduct(location)) || t.isProduct(n, location))
    			{
    				ProductType pt = t.getProduct(n);
    				int i=0;

    				for (Type member: pt.types)
    				{
    					TypeSet ts = result.get(i);

    					if (ts == null)
    					{
    						ts = new TypeSet();
    						result.put(i, ts);
    					}

    					ts.add(member);
    					i++;
    				}
    			}
    		}

    		TypeList list = new TypeList();

    		for (int i=0; i<result.size(); i++)
    		{
    			list.add(result.get(i).getType(location));
    		}

    		prodType = list.isEmpty() ? null : new ProductType(location, list);
		}

		return prodType;
	}

	@Override
	public FunctionType getFunction()
	{
		if (!funDone)
		{
    		funDone = true;
    		funcType = new UnknownType(location).getFunction();

       		TypeSet result = new TypeSet();
       		Map<Integer, TypeSet> params = new HashMap<Integer, TypeSet>();
			DefinitionList defs = new DefinitionList();

    		for (Type t: types)
    		{
    			if (t.isFunction(location))
    			{
    				if (t.definitions != null) defs.addAll(t.definitions);
    				FunctionType f = t.getFunction();
    				result.add(f.result);

    				for (int p=0; p < f.parameters.size(); p++)
    				{
    					Type pt = f.parameters.get(p);
    					TypeSet pset = params.get(p);

    					if (pset == null)
    					{
    						pset = new TypeSet(pt);
    						params.put(p, pset);
    					}
    					else
    					{
    						pset.add(pt);
    					}
    				}
    			}
    		}

    		if (!result.isEmpty())
    		{
    			Type rtype = result.getType(location);
    			TypeList plist = new TypeList();

    			for (int i=0; i<params.size(); i++)
    			{
    				Type pt = params.get(i).getType(location);
    				plist.add(pt);
    			}

    			funcType = new FunctionType(location, true, plist, rtype);
    			funcType.definitions = defs;
    		}
    		else
    		{
    			funcType = null;
    		}
    	}

		return funcType;
	}

	@Override
	public OperationType getOperation()
	{
		if (!opDone)
		{
    		opDone = true;
    		opType = new UnknownType(location).getOperation();

       		TypeSet result = new TypeSet();
       		Map<Integer, TypeSet> params = new HashMap<Integer, TypeSet>();
			DefinitionList defs = new DefinitionList();

    		for (Type t: types)
    		{
    			if (t.isOperation(location))
    			{
    				if (t.definitions != null) defs.addAll(t.definitions);
    				OperationType op = t.getOperation();
    				result.add(op.result);

    				for (int p=0; p < op.parameters.size(); p++)
    				{
    					Type pt = op.parameters.get(p);
    					TypeSet pset = params.get(p);

    					if (pset == null)
    					{
    						pset = new TypeSet(pt);
    						params.put(p, pset);
    					}
    					else
    					{
    						pset.add(pt);
    					}
    				}
    			}
    		}

    		if (!result.isEmpty())
    		{
    			Type rtype = result.getType(location);
       			TypeList plist = new TypeList();

    			for (int i=0; i<params.size(); i++)
    			{
    				Type pt = params.get(i).getType(location);
    				plist.add(pt);
    			}

    			opType = new OperationType(location, plist, rtype);
    			opType.definitions = defs;
    		}
    		else
    		{
    			opType = null;
    		}
    	}

		return opType;
	}

	@Override
	public boolean equals(Object other)
	{
		other = deBracket(other);

		if (other instanceof UnionType)
		{
			UnionType uother = (UnionType)other;

			for (Type t: uother.types)
			{
				if (!types.contains(t))
				{
					return false;
				}
			}

			return true;
		}

		return types.contains(other);
	}

	@Override
	public int hashCode()
	{
		return types.hashCode();
	}

	private void expand()
	{
		if (expanded) return;
		TypeSet exptypes = new TypeSet();

		for (Type t: types)
		{
    		if (t instanceof UnionType)
    		{
    			UnionType ut = (UnionType)t;
  				ut.expand();
   				exptypes.addAll(ut.types);
    		}
    		else
    		{
    			exptypes.add(t);
    		}
		}

		types = exptypes;
		expanded = true;
		definitions = new DefinitionList();

		for (Type t: types)
		{
			if (t.definitions != null)
			{
				definitions.addAll(t.definitions);
			}
		}
	}

	@Override
	public void unResolve()
	{
		if (!resolved) return; else { resolved = false; }

		for (Type t: types)
		{
			t.unResolve();
		}
	}

	private boolean infinite = false;

	@Override
	public Type typeResolve(Environment env, TypeDefinition root)
	{
		if (resolved)
		{
			return this;
		}
		else
		{
			resolved = true;
			infinite = true;
		}

		TypeSet fixed = new TypeSet();
		TypeCheckException problem = null;

		for (Type t: types)
		{
			if (root != null)
				root.infinite = false;

			try
			{
				fixed.add(t.typeResolve(env, root));
			}
			catch (TypeCheckException e)
			{
				if (problem == null)
				{
					problem = e;
				}
				else
				{
					// Add extra messages to the exception for each union member
					problem.addExtra(e);
				}
			}

			if (root != null)
				infinite = infinite && root.infinite;
		}
		
		if (problem != null)
		{
			unResolve();
			throw problem;
		}

		types = fixed;
		if (root != null) root.infinite = infinite;

		// Resolved types may be unions, so force a re-expand
		expanded = false;
		expand();

		return this;
	}

	@Override
	public Type polymorph(LexNameToken pname, Type actualType)
	{
		TypeSet polytypes = new TypeSet();

		for (Type type: types)
		{
			polytypes.add(type.polymorph(pname, actualType));
		}

		return new UnionType(location, polytypes);
	}

	@Override
	public String toDisplay()
	{
		if (types.size() == 1)
		{
			return types.iterator().next().toString();
		}
		else
		{
			return Utils.setToString(types, " | ");
		}
	}

	@Override
	public ValueList getAllValues(Context ctxt) throws ValueException
	{
		ValueList v = new ValueList();

		for (Type type: types)
		{
			v.addAll(type.getAllValues(ctxt));
		}

		return v;
	}
	
	@Override
	public TypeList getComposeTypes()
	{
		return types.getComposeTypes();
	}
}
