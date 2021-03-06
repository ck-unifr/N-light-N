/*****************************************************
  N-light-N
  
  A Highly-Adaptable Java Library for Document Analysis with
  Convolutional Auto-Encoders and Related Architectures.
  
  -------------------
  Author:
  2016 by Mathias Seuret <mathias.seuret@unifr.ch>
      and Michele Alberti <michele.alberti@unifr.ch>
  -------------------

  This software is free software; you can redistribute it and/or
  modify it under the terms of the GNU Lesser General Public
  License as published by the Free Software Foundation version 3.

  This software is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public
  License along with this software; if not, write to the Free Software
  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 ******************************************************************************/

package diuf.diva.dia.ms.script.command;

import diuf.diva.dia.ms.ml.ae.scae.SCAE;
import diuf.diva.dia.ms.script.XMLScript;
import diuf.diva.dia.ms.util.DataBlock;
import org.jdom2.Element;

/**
 * This command reconstructs an image and indicates how accurate the
 * reconstruction was.
 * @author Mathias Seuret
 */
public class EvaluateReconstruction extends AbstractCommand {

    public EvaluateReconstruction(XMLScript script) {
        super(script);
    }

    @Override
    public String execute(Element element) throws Exception {
        String fName = readElement(element, "image");
        int offsetX  = Integer.parseInt(readElement(element, "offset-x"));
        int offsetY  = Integer.parseInt(readElement(element, "offset-y"));

        DataBlock idb = new DataBlock(fName);
        
        for (Element e : element.getChildren("scae")) {
            String ref = readElement(e);
            SCAE scae = script.scae.get(ref);
            if (scae==null) {
                error("cannot find SCAE "+ref);
            }
            script.print("SCAE Reconstruction evaluation: ");
            float val = scae.getReconstructionScore(idb, offsetX, offsetY, SCAE.EUCLIDIAN_DISTANCE).toArray()[0];
            System.out.print(val + "\n");
        }
        return "";
    }

    @Override
    public String tagName() {
        return "evaluate-reconstruction";
    }
    
}
