import { useCallback, useEffect, useState } from "react";
import { api, pageUrl } from "./api.js";

export function useApi(path) {
  const [state, setState] = useState({
    data: null,
    loading: true,
    error: null,
  });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ data: null, loading: true, error: null });
    api(path, { signal: controller.signal }).then(
      (data) => {
        if (!controller.signal.aborted)
          setState({ data, loading: false, error: null });
      },
      (error) => {
        if (!controller.signal.aborted)
          setState({ data: null, loading: false, error });
      },
    );
    return () => controller.abort();
  }, [path, revision]);
  const reload = useCallback(() => setRevision((n) => n + 1), []);
  return { ...state, reload };
}

export function usePagination(path) {
  const [first, setFirst] = useState(10);
  const [cursors, setCursors] = useState([null]);
  const result = useApi(pageUrl(path, first, cursors.at(-1)));
  return {
    ...result,
    first,
    page: cursors.length,
    previous: () =>
      setCursors((current) =>
        current.length > 1 ? current.slice(0, -1) : current,
      ),
    next: () => {
      if (
        !result.loading &&
        result.data?.pageInfo.hasNextPage &&
        result.data.pageInfo.endCursor
      ) {
        setCursors((current) => [...current, result.data.pageInfo.endCursor]);
      }
    },
    resize: (size) => {
      setFirst(Number(size));
      setCursors([null]);
    },
  };
}
