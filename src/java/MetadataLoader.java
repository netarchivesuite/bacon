/*
 * Copyright 2011 Internet Archive
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.io.*;
import java.util.*;

import org.apache.hadoop.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;

import org.apache.pig.*;
import org.apache.pig.data.*;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigSplit;
import org.apache.pig.backend.executionengine.ExecException;

import org.apache.nutch.parse.*;
import org.apache.nutch.metadata.Metadata;


/**
 * Apache Pig UDF to load outlinks from a Nutch(WAX) segment.
 *
 * This loader assumes that the path is to the 'parse_data'
 * sub-directory of a Nutch(WAX) segment.
 *
 * It returns a Tuple for each link, of the form:
 *   (from:chararray,to:chararray,anchor:chararray)
 */
public class MetadataLoader extends LoadFunc
{
  private RecordReader<Text,Writable> reader;
  
  
  /**
   * The Nutch(WAX) "parse_data" segment is just a SequenceFile
   * with Text keys and Writable values.
   */ 
  public InputFormat getInputFormat( )
    throws IOException
  {
    return new SequenceFileInputFormat<Text,Writable>( );
  }

  /**
   * This method is slightly complicated because each NutchWAX record
   * contains all the outlinks for that page.  Thus 1 NW record can
   * produce many Tuples.  So, this code advances to the next record,
   * saves its place and emits Tuples for all the links; then advances
   * to the next record.
   *
   * Any 'null' values are mapped to "" for simplicity.
   */
  public Tuple getNext( )
    throws IOException
  {
    try 
      {
        if ( ! reader.nextKeyValue( ) )
          {
            return null;
          }
        
        Writable value;

        while ( ( value = this.reader.getCurrentValue( ) ) != null )
          {
            // Whoah, what to do?  Skip it for now
            if ( ! ( value instanceof ParseData ) ) continue ;
                    
            ParseData pd = (ParseData) value;

            String title = pd.getTitle( ); 
            title = title == null ? "" : title;
            
            Metadata meta = pd.getContentMeta( );

            Tuple tuple = TupleFactory.getInstance( ).newTuple( );
            tuple.append( get( meta, "url"        ) );
            tuple.append( title );
            tuple.append( get( meta, "length"     ) );
            tuple.append( get( meta, "date"       ) );
            tuple.append( get( meta, "type"       ) );
            tuple.append( get( meta, "collection" ) );
            
            return tuple;
          }

        return null;
      }
    catch ( InterruptedException e ) 
      {
        // From the Pig example/howto code.
        int errCode = 6018;
        String errMsg = "Error while reading input";
        throw new ExecException(errMsg, errCode,PigException.REMOTE_ENVIRONMENT, e);
      }
  }

  /**
   * Convenience function to ensure no nulls, only empty strings.
   */
  private String get( Metadata meta, String key )
  {
    String value = meta.get( key );

    if ( value == null ) return "";

    return value;
  }


  /**
   * Just save the given reader.  Dunno what to do with the 'split'.
   */
  public void prepareToRead( RecordReader reader, PigSplit split )
    throws IOException
  {
    this.reader = reader;
  }

  /**
   * The 'location' is a path string, which could contain wildcards.
   * Expand the wildcards and add each matching path to the input.
   */
  public void setLocation( String location, Job job )
    throws IOException
  {
    // Can we do this?
    //   
    // MultipleInputs.addInputPath( conf, new Path( p, "parse_data" ), SequenceFileInputFormat.class, Map.class );
    // MultipleInputs.addInputPath( conf, new Path( p, "parse_text" ), SequenceFileInputFormat.class, Map.class );

    // For now, let's assume the user gave the full path to the parse_data subdir.

    // Expand any filename globs, and add each to the input paths.
    FileStatus[] files = FileSystem.get( job.getConfiguration( ) ).globStatus( new Path( location ) );

    for ( FileStatus file : files )
      {
        FileInputFormat.addInputPath( job, file.getPath( ) );
      }
  }

}
