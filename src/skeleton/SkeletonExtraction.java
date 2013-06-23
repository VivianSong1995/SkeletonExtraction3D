package skeleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import math.Ray;
import math.Triangle;
import math.geom3d.Point3D;
import math.geom3d.Vector3D;


public class SkeletonExtraction {

	private static final double C_INF = 0.000001;
	private final int PUSHING_FACTOR;
	private final int ITERATIONS;
	private final int STEP;
	private Vector3D[] rayDirections = {
			new Vector3D(1,0,0),
			new Vector3D(0,1,0),
			new Vector3D(0,0,1),
			new Vector3D(-1,0,0),
			new Vector3D(0,-1,0),
			new Vector3D(0,0,-1),
			new Vector3D(1,1,1),
			new Vector3D(-1,-1,-1),
			new Vector3D(1,1,-1),
			new Vector3D(-1,-1,1),
			new Vector3D(-1,1,1),
			new Vector3D(1,-1,-1),
			new Vector3D(-1,1,-1),
			new Vector3D(1,-1,1)
	};
	
	private Vector<Triangle> triangles;
	
	private Vector<Chain> chains = new Vector<>();
	
	public SkeletonExtraction(Vector<Triangle> triangles, int pushingFactor, 
			int iterations, int step){
		
		this.triangles = triangles;
		
		this.PUSHING_FACTOR = pushingFactor;
		this.ITERATIONS = iterations;
		this.STEP = step;
	}
	
	public Vector<Chain> getChain(){
		return this.chains;
	}
	/**
	 * Run the algorithm to generate bones and joints
	 */
	public void getVDS(Vector<Node<Triangle>> vertices){
		
		System.out.println("Prossesing skeleton workers please wait..");
		
		ExecutorService executor = Executors.newFixedThreadPool(vertices.size()/2);
		
		for(Node<Triangle> v : vertices){
			//System.out.println("Node: "+v.getX()+" "+v.getY()+" "+v.getZ());
			Runnable worker = new Worker(v);
			executor.execute(worker);
		}
		executor.shutdown();
		while(!executor.isTerminated());
		System.out.println("Skeleton extraction finished");
	}

	/**
	 * Locates the local minimum of a seed point using the vrf(x), termination
	 * condition |vrf(x+1)|>|vrf(x)| (not working so iteration inserted) so
	 * iteration factor inserted.
	 *
	 * @param seed the initial position
	 * @param edges the edges of the polygon
	 * @return a set of continues points
	 */
	private Chain localeMinimum(Point3D seed){

		Chain result = new Chain();
		Vector3D vrft, vrft_1, norm, temp;
		Point3D seed_1;

		//visible repulsive force t
		vrft = vrf(seed, visibleSet(seed));
		int i = 0;
		
		while(i<ITERATIONS){

			norm = vrft.normalize();
			norm.times(STEP);
			
			temp = (new Vector3D(seed)).minus(norm);
			seed_1 = new Point3D(temp.getX(), temp.getY(), temp.getZ());

			vrft_1 = vrf(seed_1, visibleSet(seed_1));

			/*
			//break condition
			if(vrft_1.norm()>vrft.norm()){
				break;
			}
			*/
			
			result.addToChain(seed_1);
			
			seed = seed_1;
			vrft = vrft_1;
			i++;
		}

		return result;
	}

	/**
	 * Computes the visible set of a given point, with sampling rays and
	 * intersection points
	 */
	private ArrayList<Point3D> visibleSet(Point3D x){
		
		ArrayList<Point3D> positions = new ArrayList<>();
		ArrayList<RayPosition> list = new ArrayList<>();
		//System.out.println(x.getX()+" "+x.getY()+" "+x.getZ());
		for(Vector3D direction: rayDirections){
			//x = new Point3D(0, 0, 0);
			Ray ray = new Ray(x, direction);
			//System.out.println(x.getX()+" "+x.getY()+" "+x.getZ());
			
			for(Triangle t: triangles){
				double[] distance = new double[1];
				if(ray.intersectRayTriangle(t, distance)){
					list.add(new RayPosition(distance[0], direction));
				}
			}
			if(list.isEmpty()) continue;
			//System.out.println("Size: "+list.size());
			RayPosition min = Collections.min(list, new Comparator<RayPosition>() {

				@Override
				public int compare(RayPosition o1, RayPosition o2) {
					if(o1.dist<o2.dist){
						return -1;
					}else if(o1.dist>o2.dist){
						return 1;
					}else{
						return 0;
					}
				}
			});
			
			Vector3D temp = new Vector3D(min.direction.getX()*min.dist,
					min.direction.getY()*min.dist,
					min.direction.getZ()*min.dist);
			
			positions.add(new Point3D(x.getX()+temp.getX(),
									x.getY()+temp.getY(),
									x.getZ()+temp.getZ()));
			
			list.clear();
		}
		
		return positions;
	}
	
	/**
	 * VRF(x)~ = Σf(||vi-x||2)*(vi-x)~
	 * 
	 * @param x interior of surface S
	 * @param visibleSet the visible set
	 * @return the visible repulsive force
	 */
	private Vector3D vrf(Point3D x, ArrayList<Point3D> visibleSet){
		
		Double scalar;
		Vector3D result = new Vector3D(0, 0, 0);
		Vector3D vector;
		
		for(Point3D v: visibleSet){
			scalar = f(norm2(v, x));
			//vector = Vector3D.minus(new Vector3D(v), new Vector3D(x));
			vector = (new Vector3D(v)).minus(new Vector3D(x));
			//vector.multiply(scalar);
			vector = vector.times(scalar);
			result = result.plus(vector);
		}
		
		return result;
	}
	
	/**
	 * f(x) = 1/(x^2+c). The constant is added not to get inf. 
	 * 
	 * @param x input
	 * @return result
	 */
	private double f(double x){
    	return 1/(Math.pow(x, 2)+C_INF);
    }
	
	/**
	 * Norm ||p1-p2||2
	 * 
	 * @param p1 point1
	 * @param p2 point2
	 * @return norm2
	 */
	private double norm2(Point3D p1, Point3D p2){
    	return Math.sqrt(Math.pow(p1.getX()-p2.getX(), 2)+
    					Math.pow(p1.getY()-p2.getY(), 2)+
    					Math.pow(p1.getZ()-p2.getZ(), 2));
    }
	
	/**
	 * Pushes the point interior to the surface S
	 */
	private Point3D pushInside(Node<Triangle> p){
		
		Vector3D direction = new Vector3D(0, 0, 0);
		
		for(Triangle t : p.getAttachedTriangles()){
			
			direction = direction.plus(t.getNormal().times(-1));
			
		}
		
		direction = direction.times(PUSHING_FACTOR);
		
		//System.out.println("d: "+direction.getX()+" "+direction.getY()+" "+direction.getZ());
		Vector3D result = new Vector3D(p.getPoint());
		//System.out.println("b :"+result.getX()+" "+result.getY()+" "+result.getZ());
		result = result.plus(direction);
		//System.out.println("a: "+result.getX()+" "+result.getY()+" "+result.getZ());
		return new Point3D(result.getX(), result.getY(), result.getZ());
	}
	
	private class RayPosition{
		public double dist;
		public Vector3D direction;
		
		public RayPosition(double distance, Vector3D direction){
			this.dist = distance;
			this.direction = direction;
		}
	}
	
	private class Worker implements Runnable{
		
		private Node<Triangle> v;
		
		public Worker(Node<Triangle> v){
			this.v = v;
		}
		
		@Override
		public void run(){
			Chain temp = new Chain();
			temp = localeMinimum(pushInside(v));
			synchronized (chains) {
				chains.add(temp);
			}
			System.out.println("Node: "+v.getX()+" "+v.getY()+" "+v.getZ()+" Finished");
		}
	}
}
